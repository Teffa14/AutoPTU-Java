#!/usr/bin/env python3
"""Freeze the pinned Python combatant-to-coordinate distance helper used by forced movement."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path

SOURCE = Path("auto_ptu/rules/battle_state.py")
FUNCTION = "_combatant_distance_to_coord"


def compact(node: ast.AST) -> str:
    return " ".join(ast.unparse(node).split()).replace("\t", " ")


def call_name(call: ast.Call) -> str:
    if isinstance(call.func, ast.Attribute):
        return call.func.attr
    if isinstance(call.func, ast.Name):
        return call.func.id
    return ""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_root / SOURCE
    tree = ast.parse(source.read_text(encoding="utf-8-sig"), filename=str(source))
    matches = [
        node for node in ast.walk(tree)
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == FUNCTION
    ]
    if len(matches) != 1:
        raise SystemExit(f"expected exactly one {FUNCTION}, found {len(matches)}")
    fn = matches[0]

    arguments = ",".join(arg.arg for arg in (*fn.args.posonlyargs, *fn.args.args, *fn.args.kwonlyargs))
    rows: list[tuple[str, int, str]] = [
        ("signature", fn.lineno, arguments),
        ("implementation", fn.lineno, compact(fn)),
    ]

    returns = [node for node in ast.walk(fn) if isinstance(node, ast.Return)]
    for node in sorted(returns, key=lambda item: item.lineno):
        rows.append(("return", node.lineno, compact(node)))

    calls = [node for node in ast.walk(fn) if isinstance(node, ast.Call)]
    for node in sorted(calls, key=lambda item: item.lineno):
        rows.append((f"call:{call_name(node)}", node.lineno, compact(node)))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["path\tfunction\trole\tline\tcontract"]
    for role, line, contract in rows:
        lines.append("\t".join((SOURCE.as_posix(), FUNCTION, role, str(line), contract)))
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
