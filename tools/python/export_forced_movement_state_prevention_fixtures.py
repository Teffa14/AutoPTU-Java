#!/usr/bin/env python3
"""Freeze status/temporary-effect prevention inside pinned Python apply_forced_movement()."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path

SOURCE = Path("auto_ptu/rules/battle_state.py")
FUNCTION = "apply_forced_movement"
TOKENS = ("Ingrain", "push_immunity")


def compact(node: ast.AST, limit: int = 1800) -> str:
    return " ".join(ast.unparse(node).split()).replace("\t", " ")[:limit]


def find_function(tree: ast.AST) -> ast.FunctionDef | ast.AsyncFunctionDef:
    matches = [
        node for node in ast.walk(tree)
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == FUNCTION
    ]
    if len(matches) != 1:
        raise SystemExit(f"expected exactly one {FUNCTION}, found {len(matches)}")
    return matches[0]


def parent_map(root: ast.AST) -> dict[ast.AST, ast.AST]:
    parents: dict[ast.AST, ast.AST] = {}
    for parent in ast.walk(root):
        for child in ast.iter_child_nodes(parent):
            parents[child] = parent
    return parents


def enclosing_guards(node: ast.AST, parents: dict[ast.AST, ast.AST]) -> str:
    guards: list[str] = []
    current = parents.get(node)
    while current is not None:
        if isinstance(current, ast.If):
            guards.append(compact(current.test, 700))
        current = parents.get(current)
    guards.reverse()
    return " && ".join(guards)


def best_match(fn: ast.AST, token: str) -> ast.AST:
    matches: list[ast.AST] = []
    needle = token.lower()
    for node in ast.walk(fn):
        if not isinstance(node, (ast.If, ast.For)):
            continue
        if needle in compact(node).lower():
            matches.append(node)
    if not matches:
        raise SystemExit(f"pinned {FUNCTION} no longer contains {token!r}")
    # Prefer the smallest relevant branch so the fixture tracks the rule itself,
    # not the full enclosing function or unrelated downstream statements.
    return min(matches, key=lambda node: len(compact(node, 100000)))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_root / SOURCE
    tree = ast.parse(source.read_text(encoding="utf-8-sig"), filename=str(source))
    fn = find_function(tree)
    parents = parent_map(fn)

    rows: list[tuple[str, int, str, str]] = []
    for token in TOKENS:
        node = best_match(fn, token)
        guard = compact(node.test, 700) if isinstance(node, ast.If) else enclosing_guards(node, parents)
        rows.append((token, node.lineno, guard, compact(node)))

    rendered = "\n".join(row[2] + " " + row[3] for row in rows).lower()
    if "ingrain" not in rendered:
        raise SystemExit("Ingrain prevention disappeared from pinned oracle")
    if "push_immunity" not in rendered:
        raise SystemExit("push_immunity prevention disappeared from pinned oracle")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["source\tline\tguard\tbranch"]
    lines.extend("\t".join(str(value).replace("\t", " ") for value in row) for row in rows)
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
