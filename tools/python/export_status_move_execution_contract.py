#!/usr/bin/env python3
"""Freeze non-damaging Status move execution semantics from pinned AutoPTU Python."""

from __future__ import annotations

import argparse
import ast
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args()


def find_function(root: ast.AST, name: str) -> ast.FunctionDef:
    for node in ast.walk(root):
        if isinstance(node, ast.FunctionDef) and node.name == name:
            return node
    raise AssertionError(f"Could not locate function {name}")


def status_return(resolve_move_action: ast.FunctionDef) -> ast.Return:
    for node in ast.walk(resolve_move_action):
        if not isinstance(node, ast.If):
            continue
        test = ast.unparse(node.test).lower()
        if "move.category.lower()" not in test or "status" not in test:
            continue
        for statement in node.body:
            if isinstance(statement, ast.Return) and isinstance(statement.value, ast.Dict):
                return statement
    raise AssertionError("Could not locate Status return in resolve_move_action")


def dict_values(node: ast.Return) -> dict[str, ast.AST]:
    assert isinstance(node.value, ast.Dict)
    result: dict[str, ast.AST] = {}
    for key, value in zip(node.value.keys, node.value.values):
        if isinstance(key, ast.Constant) and isinstance(key.value, str):
            result[key.value] = value
    return result


def is_literal(node: ast.AST | None, expected: object) -> bool:
    return isinstance(node, ast.Constant) and node.value == expected


def hit_uses_accuracy(node: ast.AST | None) -> bool:
    if node is None:
        return False
    source = ast.unparse(node)
    return "accuracy" in source and "hit" in source


def main() -> int:
    args = parse_args()
    source_root = Path(args.source_root).resolve()
    tree = ast.parse(
        (source_root / "auto_ptu" / "rules" / "battle_state.py").read_text(encoding="utf-8")
    )
    resolve_move_action = find_function(tree, "resolve_move_action")
    status_result = status_return(resolve_move_action)
    values = dict_values(status_result)

    contract = {
        "status_branch_present": True,
        "hit_comes_from_accuracy_result": hit_uses_accuracy(values.get("hit")),
        "crit_is_always_false": is_literal(values.get("crit"), False),
        "damage_is_always_zero": is_literal(values.get("damage"), 0),
        "damage_roll_is_always_zero": is_literal(values.get("damage_roll"), 0),
    }

    assert all(contract.values()), contract

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\t".join(
            [
                "STATUS_MOVE_EXECUTION",
                *("1" if value else "0" for value in contract.values()),
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
