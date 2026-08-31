#!/usr/bin/env python3
"""Freeze the pinned Python footprint geometry used by combatant distance."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path

SOURCE = Path("auto_ptu/rules/targeting.py")
FUNCTIONS = ("footprint_side_for_size", "footprint_tiles", "footprint_distance", "chebyshev_distance")


def compact(node: ast.AST) -> str:
    return " ".join(ast.unparse(node).split()).replace("\t", " ")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_root / SOURCE
    tree = ast.parse(source.read_text(encoding="utf-8-sig"), filename=str(source))
    functions = {
        node.name: node
        for node in tree.body
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name in FUNCTIONS
    }
    missing = [name for name in FUNCTIONS if name not in functions]
    if missing:
        raise SystemExit(f"missing footprint helpers: {missing}")

    footprint_map = next(
        (
            node for node in tree.body
            if isinstance(node, ast.Assign)
            and any(isinstance(target, ast.Name) and target.id == "_FOOTPRINT_BY_SIZE" for target in node.targets)
        ),
        None,
    )
    if footprint_map is None:
        raise SystemExit("missing _FOOTPRINT_BY_SIZE")

    lines = ["path\tsymbol\trole\tline\tcontract"]
    lines.append("\t".join((SOURCE.as_posix(), "_FOOTPRINT_BY_SIZE", "mapping", str(footprint_map.lineno), compact(footprint_map))))
    for name in FUNCTIONS:
        fn = functions[name]
        lines.append("\t".join((SOURCE.as_posix(), name, "implementation", str(fn.lineno), compact(fn))))
        for node in sorted((n for n in ast.walk(fn) if isinstance(n, ast.Return)), key=lambda n: n.lineno):
            lines.append("\t".join((SOURCE.as_posix(), name, "return", str(node.lineno), compact(node))))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
