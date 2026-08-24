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

    context_has_damage_dealt = "damage_dealt: int" in specials_source
    context_forwards_damage_dealt = "damage_dealt=damage_dealt" in handle
    post_marker = 'phase="post_damage"'
    post_index = resolve_targets.find(post_marker)
    post_passes_applied_damage = post_index >= 0 and "damage_dealt=damage" in resolve_targets[max(0, post_index - 800):post_index + 400]

    damage_apply_index = resolve_targets.find("_apply_damage_with_injury_rules")
    history_index = resolve_targets.find("_record_damage_exchange", damage_apply_index if damage_apply_index >= 0 else 0)
    post_after_hp_and_history = damage_apply_index >= 0 and history_index >= 0 and post_index > history_index

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
