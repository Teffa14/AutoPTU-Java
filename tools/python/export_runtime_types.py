#!/usr/bin/env python3
"""Export observed Python runtime types to guide the AutoPTU Java port.

Run selected AutoPTU pytest scenarios in-process while tracing calls inside the
requested source prefixes. Only type shapes are recorded; argument values are not
written to the manifest.

Example from an AutoPTU-Java checkout next to AutoPTU::

    python tools/python/export_runtime_types.py \
      --source-root ../AutoPTU \
      --include auto_ptu/rules \
      --output build/python-rule-types.json \
      -- tests/test_battle_state.py -q
"""
from __future__ import annotations

import argparse
import inspect
import json
import os
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any


def type_shape(value: Any, *, depth: int = 0) -> str:
    if value is None:
        return "None"
    cls = type(value)
    base = f"{cls.__module__}.{cls.__qualname__}"
    if depth >= 1:
        return base

    if isinstance(value, (list, tuple, set, frozenset)):
        members = sorted({type_shape(item, depth=depth + 1) for item in list(value)[:16]})
        if not members:
            return f"{base}[]"
        return f"{base}[{' | '.join(members)}]"

    if isinstance(value, dict):
        sample = list(value.items())[:16]
        if not sample:
            return f"{base}{{}}"
        keys = sorted({type_shape(key, depth=depth + 1) for key, _ in sample})
        vals = sorted({type_shape(item, depth=depth + 1) for _, item in sample})
        return f"{base}[{' | '.join(keys)} -> {' | '.join(vals)}]"

    return base


class RuntimeTypeTracer:
    def __init__(self, source_root: Path, includes: list[str]) -> None:
        self.source_root = source_root.resolve()
        self.includes = tuple(self._normalize_prefix(value) for value in includes)
        self.records: dict[str, dict[str, Any]] = {}

    @staticmethod
    def _normalize_prefix(value: str) -> str:
        return value.replace("\\", "/").strip("/")

    def _relative_source(self, frame) -> str | None:
        try:
            path = Path(frame.f_code.co_filename).resolve()
            relative = path.relative_to(self.source_root).as_posix()
        except (ValueError, OSError):
            return None
        if self.includes and not any(
            relative == prefix or relative.startswith(prefix + "/")
            for prefix in self.includes
        ):
            return None
        return relative

    def __call__(self, frame, event: str, arg):
        if event not in {"call", "return"}:
            return self
        relative = self._relative_source(frame)
        if relative is None:
            return self

        code = frame.f_code
        qualname = getattr(code, "co_qualname", code.co_name)
        key = f"{relative}:{code.co_firstlineno}:{qualname}"
        record = self.records.setdefault(
            key,
            {
                "file": relative,
                "line": int(code.co_firstlineno),
                "qualname": qualname,
                "calls": 0,
                "arguments": defaultdict(set),
                "returns": set(),
            },
        )

        if event == "call":
            record["calls"] += 1
            try:
                args = inspect.getargvalues(frame)
            except Exception:
                return self

            names = list(args.args)
            if args.varargs:
                names.append(args.varargs)
            if args.keywords:
                names.append(args.keywords)
            for name in names:
                if name not in frame.f_locals:
                    continue
                try:
                    record["arguments"][name].add(type_shape(frame.f_locals[name]))
                except Exception as exc:
                    record["arguments"][name].add(f"<trace-error:{type(exc).__name__}>")
        else:
            try:
                record["returns"].add(type_shape(arg))
            except Exception as exc:
                record["returns"].add(f"<trace-error:{type(exc).__name__}>")
        return self

    def manifest(self) -> dict[str, Any]:
        functions: dict[str, Any] = {}
        for key in sorted(self.records):
            record = self.records[key]
            functions[key] = {
                "file": record["file"],
                "line": record["line"],
                "qualname": record["qualname"],
                "calls": record["calls"],
                "arguments": {
                    name: sorted(values)
                    for name, values in sorted(record["arguments"].items())
                },
                "returns": sorted(record["returns"]),
            }
        return {
            "schema_version": 1,
            "source_root": str(self.source_root),
            "includes": list(self.includes),
            "function_count": len(functions),
            "functions": functions,
        }


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--include", action="append", default=[])
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument(
        "pytest_args",
        nargs=argparse.REMAINDER,
        help="pytest arguments after --",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    launch_cwd = Path.cwd().resolve()
    source_root = args.source_root.resolve()
    if not source_root.exists():
        raise SystemExit(f"source root does not exist: {source_root}")

    pytest_args = list(args.pytest_args)
    if pytest_args and pytest_args[0] == "--":
        pytest_args = pytest_args[1:]
    if not pytest_args:
        pytest_args = ["tests/test_battle_state.py", "-q"]

    output = args.output if args.output.is_absolute() else (launch_cwd / args.output)
    output = output.resolve()

    sys.path.insert(0, str(source_root))
    os.chdir(source_root)

    try:
        import pytest
    except ImportError as exc:
        raise SystemExit("pytest must be installed in the Python AutoPTU environment") from exc

    tracer = RuntimeTypeTracer(source_root, args.include or ["auto_ptu/rules"])
    old_profile = sys.getprofile()
    sys.setprofile(tracer)
    try:
        exit_code = int(pytest.main(pytest_args))
    finally:
        sys.setprofile(old_profile)

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(tracer.manifest(), indent=2, sort_keys=True),
        encoding="utf-8",
    )
    print(f"wrote {len(tracer.records)} function type records to {output}")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
