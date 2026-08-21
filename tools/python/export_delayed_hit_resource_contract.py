#!/usr/bin/env python3
"""Freeze resource-bookkeeping semantics for matured delayed hits.

The pinned Python implementation records action/frequency bookkeeping on the normal
UseMoveAction/ActionResolver path. A matured delayed hit bypasses that declaration path
and enters BattleState.resolve_move_targets directly, where the actual attack resolver
runs. Java must preserve that split so maturity does not double-spend resources.
"""

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

    battle_path = source_root / "auto_ptu" / "rules" / "battle_state.py"
    delayed_path = source_root / "auto_ptu" / "rules" / "hooks" / "move_effect_tools.py"
    action_resolver_path = source_root / "auto_ptu" / "rules" / "controllers" / "action_resolver.py"
    battle_tree = ast.parse(battle_path.read_text(encoding="utf-8"))
    delayed_tree = ast.parse(delayed_path.read_text(encoding="utf-8"))
    action_resolver_tree = ast.parse(action_resolver_path.read_text(encoding="utf-8"))

    move_action = find_class(battle_tree, "UseMoveAction")
    validate_calls = call_names(find_method(move_action, "validate"))
    resolve_calls = call_names(find_method(move_action, "resolve"))
    target_calls = call_names(find_function(battle_tree, "resolve_move_targets"))
    delayed_calls = call_names(find_function(delayed_tree, "resolve_delayed_hits"))
    action_resolver = find_class(action_resolver_tree, "ActionResolver")
    normal_action_calls = call_names(find_method(action_resolver, "resolve_next_action"))

    normal_checks_frequency = "ensure_move_frequency_available" in validate_calls
    normal_records_frequency = "record_move_frequency_usage" in resolve_calls
    normal_records_move_used = "_record_move_used" in resolve_calls
    normal_marks_action = "mark_action" in normal_action_calls
    delayed_enters_target_resolution = "resolve_move_targets" in delayed_calls
    delayed_uses_action_resolver = "resolve_next_action" in delayed_calls
    target_records_frequency = "record_move_frequency_usage" in target_calls
    target_records_move_used = "_record_move_used" in target_calls
    target_marks_action = "mark_action" in target_calls
    target_resolves_attack = "resolve_move_action" in target_calls

    # Fail loudly if Python moves any responsibility. Java must then review the
    # delayed executor rather than retaining an obsolete no-double-spend policy.
    assert normal_checks_frequency
    assert normal_records_frequency
    assert normal_records_move_used
    assert normal_marks_action
    assert delayed_enters_target_resolution
    assert not delayed_uses_action_resolver
    assert not target_records_frequency
    assert not target_records_move_used
    assert not target_marks_action
    assert target_resolves_attack

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\t".join(
            [
                "DELAYED_RESOURCE_POLICY",
                "1" if delayed_enters_target_resolution else "0",
                "1" if normal_checks_frequency else "0",
                "1" if normal_records_frequency else "0",
                "1" if normal_records_move_used else "0",
                "1" if normal_marks_action else "0",
                "1" if delayed_uses_action_resolver else "0",
                "1" if target_records_frequency else "0",
                "1" if target_records_move_used else "0",
                "1" if target_marks_action else "0",
                "1" if target_resolves_attack else "0",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
