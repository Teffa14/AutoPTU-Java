#!/usr/bin/env python3
"""Freeze the pinned Python shadow_tag_anchor forced-movement geometry contract."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path

SOURCE = Path("auto_ptu/rules/battle_state.py")
FUNCTION = "apply_forced_movement"


def compact(node: ast.AST) -> str:
    return " ".join(ast.unparse(node).split()).replace("\t", " ")


def find_function(tree: ast.AST) -> ast.FunctionDef | ast.AsyncFunctionDef:
    matches = [
        node for node in ast.walk(tree)
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == FUNCTION
    ]
    if len(matches) != 1:
        raise SystemExit(f"expected exactly one {FUNCTION}, found {len(matches)}")
    return matches[0]


def call_name(call: ast.Call) -> str:
    if isinstance(call.func, ast.Attribute):
        return call.func.attr
    if isinstance(call.func, ast.Name):
        return call.func.id
    return ""


def has_string(node: ast.AST, value: str) -> bool:
    return any(isinstance(child, ast.Constant) and child.value == value for child in ast.walk(node))


def parent_map(root: ast.AST) -> dict[ast.AST, ast.AST]:
    parents: dict[ast.AST, ast.AST] = {}
    for parent in ast.walk(root):
        for child in ast.iter_child_nodes(parent):
            parents[child] = parent
    return parents


def nearest(node: ast.AST, parents: dict[ast.AST, ast.AST], kinds: tuple[type[ast.AST], ...]) -> ast.AST | None:
    current = node
    while current in parents:
        current = parents[current]
        if isinstance(current, kinds):
            return current
    return None


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

    anchor_calls = [
        node for node in ast.walk(fn)
        if isinstance(node, ast.Call)
        and call_name(node) == "get_temporary_effects"
        and has_string(node, "shadow_tag_anchor")
    ]
    if len(anchor_calls) != 1:
        raise SystemExit(f"expected one shadow_tag_anchor lookup, found {len(anchor_calls)}")
    anchor_stmt = nearest(anchor_calls[0], parents, (ast.For, ast.Assign, ast.AnnAssign, ast.Expr))
    if anchor_stmt is None:
        raise SystemExit("could not locate shadow_tag_anchor setup statement")
    rows.append(("anchor_setup", getattr(anchor_stmt, "lineno", -1), "", compact(anchor_stmt)))

    distance_calls = [
        node for node in ast.walk(fn)
        if isinstance(node, ast.Call) and call_name(node) == "_combatant_distance_to_coord"
    ]
    if len(distance_calls) != 1:
        raise SystemExit(f"expected one anchor distance call, found {len(distance_calls)}")
    guard = nearest(distance_calls[0], parents, (ast.If,))
    if not isinstance(guard, ast.If):
        raise SystemExit("shadow_tag_anchor distance call is no longer guarded by an if")
    rows.append(("candidate_guard", guard.lineno, compact(guard.test), compact(guard)))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["path\tfunction\trole\tline\tcondition\tstatement"]
    for role, line, condition, statement in sorted(rows, key=lambda row: row[1]):
        lines.append("\t".join((SOURCE.as_posix(), FUNCTION, role, str(line), condition, statement)))
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
