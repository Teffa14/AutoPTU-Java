#!/usr/bin/env python3
"""Freeze Trainer Feature + capability branches inside pinned Python apply_forced_movement()."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path

FEATURE = "Insectoid Utility"
CAPABILITY = "Wallclimber"


def compact(node: ast.AST, limit: int = 1800) -> str:
    return " ".join(ast.unparse(node).split()).replace("\t", " ")[:limit]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_root / "auto_ptu" / "rules" / "battle_state.py"
    tree = ast.parse(source.read_text(encoding="utf-8-sig"), filename=str(source))
    functions = [
        node for node in ast.walk(tree)
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == "apply_forced_movement"
    ]
    if len(functions) != 1:
        raise SystemExit(f"expected one apply_forced_movement definition, found {len(functions)}")
    function = functions[0]

    matches: list[ast.If] = []
    for node in ast.walk(function):
        if not isinstance(node, ast.If):
            continue
        rendered = compact(node).lower()
        if FEATURE.lower() in rendered and CAPABILITY.lower() in rendered:
            matches.append(node)

    if not matches:
        raise SystemExit(
            f"pinned apply_forced_movement no longer contains a branch combining {FEATURE!r} and {CAPABILITY!r}"
        )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["feature\tcapability\tline\tcondition\tbranch"]
    for node in sorted(matches, key=lambda value: value.lineno):
        lines.append("\t".join((
            FEATURE,
            CAPABILITY,
            str(node.lineno),
            compact(node.test, 800),
            compact(node, 1800),
        )))
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
