#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ast
from pathlib import Path

PHASES = {"pre_damage", "post_damage", "end_action"}


def call_name(node: ast.Call) -> str:
    func = node.func
    if isinstance(func, ast.Name):
        return func.id
    if isinstance(func, ast.Attribute):
        return func.attr
    return ""


def phase_literal(node: ast.Call) -> str | None:
    for keyword in node.keywords:
        if keyword.arg == "phase" and isinstance(keyword.value, ast.Constant) and isinstance(keyword.value.value, str):
            return keyword.value.value.strip().lower()
    return None


def function_phase_sequences(source_root: Path) -> list[tuple[str, str, list[str]]]:
    found: list[tuple[str, str, list[str]]] = []
    package = source_root / "auto_ptu"
    for path in sorted(package.rglob("*.py")):
        source = path.read_text(encoding="utf-8")
        try:
            tree = ast.parse(source)
        except SyntaxError:
            continue
        for node in ast.walk(tree):
            if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                continue
            calls: list[tuple[int, str]] = []
            for child in ast.walk(node):
                if isinstance(child, ast.Call) and call_name(child) == "handle_move_specials":
                    phase = phase_literal(child)
                    if phase in PHASES:
                        calls.append((getattr(child, "lineno", 0), phase))
            calls.sort()
            phases = [phase for _, phase in calls]
            if PHASES.issubset(set(phases)):
                found.append((str(path.relative_to(source_root)), node.name, phases))
    return found


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    matches = function_phase_sequences(Path(args.source_root))
    if not matches:
        raise RuntimeError("no Python resolver contains pre_damage, post_damage and end_action move-special dispatches")

    # Prefer the shortest complete sequence; this avoids nested helper definitions and freezes the
    # resolver that actually owns the three phase boundaries.
    path, function, phases = min(matches, key=lambda entry: (len(entry[2]), entry[0], entry[1]))
    first_pre = phases.index("pre_damage")
    first_post = phases.index("post_damage")
    first_end = phases.index("end_action")
    pre_before_post = first_pre < first_post
    post_before_end = first_post < first_end
    one_resolver = True
    values = (pre_before_post, post_before_end, one_resolver)
    if not all(values):
        raise RuntimeError(f"move-special phase order changed in {path}:{function}: {phases}")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "MOVE_SPECIAL_PIPELINE\t"
        + "\t".join("1" if value else "0" for value in values)
        + "\t" + path + "\t" + function + "\t" + ",".join(phases) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
