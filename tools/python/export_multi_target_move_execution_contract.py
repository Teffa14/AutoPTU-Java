#!/usr/bin/env python3
"""Freeze ordinary multi-target move execution ownership from pinned AutoPTU Python."""

from __future__ import annotations

import argparse
import ast
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args()


def call_name(node: ast.Call) -> str:
    target = node.func
    if isinstance(target, ast.Name):
        return target.id
    if isinstance(target, ast.Attribute):
        return target.attr
    return ""


def call_names(node: ast.AST) -> list[str]:
    return [
        name
        for item in ast.walk(node)
        if isinstance(item, ast.Call)
        if (name := call_name(item))
    ]


def count_calls(node: ast.AST, name: str) -> int:
    return sum(
        1
        for item in ast.walk(node)
        if isinstance(item, ast.Call) and call_name(item) == name
    )


def find_class(root: ast.AST, name: str) -> ast.ClassDef:
    for node in ast.walk(root):
        if isinstance(node, ast.ClassDef) and node.name == name:
            return node
    raise AssertionError(f"Could not locate class {name}")


def find_method(class_node: ast.ClassDef, name: str) -> ast.FunctionDef:
    for node in class_node.body:
        if isinstance(node, ast.FunctionDef) and node.name == name:
            return node
    raise AssertionError(f"Could not locate {class_node.name}.{name}")


def find_function(root: ast.AST, name: str) -> ast.FunctionDef:
    for node in ast.walk(root):
        if isinstance(node, ast.FunctionDef) and node.name == name:
            return node
    raise AssertionError(f"Could not locate function {name}")


def main() -> int:
    args = parse_args()
    source_root = Path(args.source_root).resolve()

    battle_tree = ast.parse(
        (source_root / "auto_ptu" / "rules" / "battle_state.py").read_text(encoding="utf-8")
    )
    action_tree = ast.parse(
        (source_root / "auto_ptu" / "rules" / "controllers" / "action_resolver.py").read_text(encoding="utf-8")
    )

    target_resolution = find_function(battle_tree, "resolve_move_targets")
    use_move_action = find_class(battle_tree, "UseMoveAction")
    use_move_validate = find_method(use_move_action, "validate")
    use_move_resolve = find_method(use_move_action, "resolve")
    action_resolver = find_class(action_tree, "ActionResolver")
    resolve_next_action = find_method(action_resolver, "resolve_next_action")

    target_calls = call_names(target_resolution)
    validate_calls = call_names(use_move_validate)
    move_resolve_calls = call_names(use_move_resolve)
    normal_action_calls = call_names(resolve_next_action)

    loops_with_move_resolution = [
        loop
        for loop in ast.walk(target_resolution)
        if isinstance(loop, (ast.For, ast.AsyncFor))
        and count_calls(loop, "resolve_move_action") > 0
    ]

    resolves_each_target_inside_loop = bool(loops_with_move_resolution)
    target_marks_action = "mark_action" in target_calls
    target_records_frequency = "record_move_frequency_usage" in target_calls
    target_records_move_used = "_record_move_used" in target_calls
    ordinary_marks_action = "mark_action" in normal_action_calls
    ordinary_checks_frequency = "ensure_move_frequency_available" in validate_calls
    ordinary_records_frequency = "record_move_frequency_usage" in move_resolve_calls
    ordinary_records_move_used = "_record_move_used" in move_resolve_calls

    # Fail loudly if Python moves bookkeeping into the per-target loop or stops resolving
    # effective targets through the ordinary move resolver. Java must then revisit AoE execution.
    assert resolves_each_target_inside_loop
    assert not target_marks_action
    assert not target_records_frequency
    assert not target_records_move_used
    assert ordinary_marks_action
    assert ordinary_checks_frequency
    assert ordinary_records_frequency
    assert ordinary_records_move_used

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\t".join(
            [
                "MULTI_TARGET_MOVE_EXECUTION",
                "1" if resolves_each_target_inside_loop else "0",
                "1" if target_marks_action else "0",
                "1" if target_records_frequency else "0",
                "1" if target_records_move_used else "0",
                "1" if ordinary_marks_action else "0",
                "1" if ordinary_checks_frequency else "0",
                "1" if ordinary_records_frequency else "0",
                "1" if ordinary_records_move_used else "0",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
