#!/usr/bin/env python3
"""Freeze whether the pinned Python oracle binds forced movement into runtime code.

This scans production Python under auto_ptu/ for executable calls to
forced_movement_instruction.  The Java port must not silently auto-bind the helper
into an attack phase until the authoritative oracle exposes such a binding.
"""

from __future__ import annotations

import argparse
import ast
from pathlib import Path

SYMBOL = "forced_movement_instruction"


def call_symbol(node: ast.Call) -> str | None:
    func = node.func
    if isinstance(func, ast.Name):
        return func.id
    if isinstance(func, ast.Attribute):
        return func.attr
    return None


def production_calls(source_root: Path) -> list[tuple[str, int]]:
    package_root = source_root / "auto_ptu"
    if not package_root.is_dir():
        raise SystemExit(f"missing oracle package: {package_root}")

    calls: list[tuple[str, int]] = []
    for path in sorted(package_root.rglob("*.py")):
        tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
        for node in ast.walk(tree):
            if isinstance(node, ast.Call) and call_symbol(node) == SYMBOL:
                calls.append((path.relative_to(source_root).as_posix(), node.lineno))
    return calls


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    calls = production_calls(args.source_root)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["symbol\truntime_call_count"]
    lines.append(f"{SYMBOL}\t{len(calls)}")
    for path, line in calls:
        lines.append(f"callsite\t{path}:{line}")
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")

    # The currently pinned oracle defines the inference helper but does not bind
    # it into production runtime execution. Freeze that negative contract so Java
    # cannot activate a speculative attack-phase ordering ahead of Python.
    if calls:
        rendered = ", ".join(f"{path}:{line}" for path, line in calls)
        raise SystemExit(
            "pinned oracle now has forced movement runtime callsites; "
            f"inspect ordering before changing Java: {rendered}"
        )


if __name__ == "__main__":
    main()
