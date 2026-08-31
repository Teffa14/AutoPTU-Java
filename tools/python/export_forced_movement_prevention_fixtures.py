#!/usr/bin/env python3
"""Freeze defender ability branches inside pinned Python apply_forced_movement()."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path

ABILITIES = ("Suction Cups", "Sumo Stance")


def compact(node: ast.AST, limit: int = 1000) -> str:
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

    rows: list[tuple[str, int, str, str]] = []
    for ability in ABILITIES:
        matches: list[ast.If] = []
        for node in ast.walk(function):
            if not isinstance(node, ast.If):
                continue
            rendered = compact(node)
            if ability.lower() in rendered.lower():
                matches.append(node)
        if not matches:
            raise SystemExit(f"pinned apply_forced_movement no longer contains {ability!r}")
        for node in matches:
            rows.append((ability, node.lineno, compact(node.test, 600), compact(node, 1400)))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["ability\tline\tcondition\tbranch"]
    lines.extend("\t".join(str(value).replace("\t", " ") for value in row) for row in rows)
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
