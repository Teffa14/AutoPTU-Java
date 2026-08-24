#!/usr/bin/env python3
"""Freeze the pinned Python POST_DAMAGE move-special transport/timing contract."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def function_source(source: str, tree: ast.Module, name: str) -> str:
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == name:
            segment = ast.get_source_segment(source, node)
            if segment:
                return segment
    raise RuntimeError(f"missing function: {name}")


def call_name(node: ast.Call) -> str:
    func = node.func
    if isinstance(func, ast.Name):
        return func.id
    if isinstance(func, ast.Attribute):
        return func.attr
    return ""


def keyword_value(node: ast.Call, name: str) -> ast.expr | None:
    for keyword in node.keywords:
        if keyword.arg == name:
            return keyword.value
    return None


def constant_string(node: ast.expr | None) -> str | None:
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    return None


def name_value(node: ast.expr | None) -> str | None:
    return node.id if isinstance(node, ast.Name) else None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    root = Path(args.source_root)
    specials_path = root / "auto_ptu" / "rules" / "hooks" / "move_specials.py"
    battle_path = root / "auto_ptu" / "rules" / "battle_state.py"
    specials_source = specials_path.read_text(encoding="utf-8")
    battle_source = battle_path.read_text(encoding="utf-8")
    specials_tree = ast.parse(specials_source)
    battle_tree = ast.parse(battle_source)

    handle = function_source(specials_source, specials_tree, "handle_move_specials")
    resolve_targets = function_source(battle_source, battle_tree, "resolve_move_targets")
    resolve_tree = ast.parse(resolve_targets)

    context_has_damage_dealt = "damage_dealt: int" in specials_source
    context_forwards_damage_dealt = "damage_dealt=damage_dealt" in handle

    post_call: ast.Call | None = None
    for node in ast.walk(resolve_tree):
        if not isinstance(node, ast.Call) or call_name(node) != "handle_move_specials":
            continue
        if constant_string(keyword_value(node, "phase")) != "post_damage":
            continue
        if name_value(keyword_value(node, "damage_dealt")) == "damage":
            post_call = node
            break

    post_passes_applied_damage = post_call is not None

    damage_apply_line = -1
    history_line = -1
    for node in ast.walk(resolve_tree):
        if not isinstance(node, ast.Call):
            continue
        if call_name(node) == "_apply_damage_with_injury_rules" and damage_apply_line < 0:
            damage_apply_line = node.lineno
        if call_name(node) == "_record_damage_exchange" and history_line < 0:
            history_line = node.lineno

    post_after_hp_and_history = (
        post_call is not None
        and damage_apply_line >= 0
        and history_line >= 0
        and post_call.lineno > damage_apply_line
        and post_call.lineno > history_line
    )

    values = [
        context_has_damage_dealt,
        context_forwards_damage_dealt,
        post_passes_applied_damage,
        post_after_hp_and_history,
    ]
    if not all(values):
        raise RuntimeError(f"move-special POST_DAMAGE contract changed: {values}")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "MOVE_SPECIAL_POST_DAMAGE\t" + "\t".join("1" if value else "0" for value in values) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
