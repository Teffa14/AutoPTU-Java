#!/usr/bin/env python3
"""Extract a stable manifest for Python BattleState._build_initiative_order()."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def find_method(tree: ast.AST, name: str) -> ast.FunctionDef:
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef) and node.name == name:
            return node
    raise RuntimeError(f"{name} not found")


def dotted_name(node: ast.AST) -> str | None:
    parts: list[str] = []
    current = node
    while isinstance(current, ast.Attribute):
        parts.append(current.attr)
        current = current.value
    if isinstance(current, ast.Name):
        parts.append(current.id)
        return ".".join(reversed(parts))
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_root.resolve() / "auto_ptu" / "rules" / "battle_state.py"
    tree = ast.parse(source.read_text(encoding="utf-8"), filename=str(source))
    method = find_method(tree, "_build_initiative_order")

    calls: set[str] = set()
    attributes: set[str] = set()
    names: set[str] = set()
    string_constants: set[str] = set()

    for node in ast.walk(method):
        if isinstance(node, ast.Call):
            name = dotted_name(node.func)
            if name:
                calls.add(name)
        elif isinstance(node, ast.Attribute):
            name = dotted_name(node)
            if name:
                attributes.add(name)
        elif isinstance(node, ast.Name) and isinstance(node.ctx, ast.Load):
            names.add(node.id)
        elif isinstance(node, ast.Constant) and isinstance(node.value, str):
            value = node.value.strip()
            if value:
                string_constants.add(value)

    rows: list[tuple[str, str]] = [
        ("method", method.name),
        ("lineno", str(method.lineno)),
        ("end_lineno", str(method.end_lineno or method.lineno)),
    ]
    rows.extend(("call", value) for value in sorted(calls))
    rows.extend(("attribute", value) for value in sorted(attributes))
    rows.extend(("name", value) for value in sorted(names))
    rows.extend(("string", value) for value in sorted(string_constants))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{kind}\t{value}" for kind, value in rows) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} initiative rebuild contract rows to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
