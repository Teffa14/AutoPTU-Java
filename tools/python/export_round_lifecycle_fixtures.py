#!/usr/bin/env python3
"""Extract the round-start lifecycle contract from Python PhaseController."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def find_start_round(tree: ast.Module) -> ast.FunctionDef:
    for node in tree.body:
        if isinstance(node, ast.ClassDef) and node.name == "PhaseController":
            for statement in node.body:
                if isinstance(statement, ast.FunctionDef) and statement.name == "start_round":
                    return statement
    raise RuntimeError("PhaseController.start_round not found")


def has_round_increment(method: ast.FunctionDef) -> bool:
    return any(
        isinstance(node, ast.AugAssign)
        and isinstance(node.target, ast.Attribute)
        and node.target.attr == "round"
        and isinstance(node.op, ast.Add)
        and isinstance(node.value, ast.Constant)
        and node.value.value == 1
        for node in ast.walk(method)
    )


def reset_targets(method: ast.FunctionDef) -> set[str]:
    targets: set[str] = set()
    for node in ast.walk(method):
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr == "reset_actions":
            if isinstance(node.func.value, ast.Name):
                targets.add(node.func.value.id)
    return targets


def removed_temporary_effect_names(method: ast.FunctionDef) -> set[str]:
    names: set[str] = set()
    for node in ast.walk(method):
        if not isinstance(node, ast.Call):
            continue
        if not isinstance(node.func, ast.Attribute) or node.func.attr != "remove_temporary_effect":
            continue
        if not node.args:
            continue
        first = node.args[0]
        if isinstance(first, ast.Constant) and isinstance(first.value, str):
            names.add(first.value)
    return names


def clears_battle_attribute(method: ast.FunctionDef, attribute: str) -> bool:
    for node in ast.walk(method):
        if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Attribute):
            continue
        if node.func.attr != "clear" or not isinstance(node.func.value, ast.Attribute):
            continue
        target = node.func.value
        if isinstance(target.value, ast.Name) and target.value.id == "battle" and target.attr == attribute:
            return True
    return False


def assignment_uses_attribute(method: ast.FunctionDef, target_attr: str, source_attr: str) -> bool:
    for node in ast.walk(method):
        if not isinstance(node, ast.Assign):
            continue
        if not any(
            isinstance(target, ast.Attribute)
            and isinstance(target.value, ast.Name)
            and target.value.id == "battle"
            and target.attr == target_attr
            for target in node.targets
        ):
            continue
        if any(
            isinstance(child, ast.Attribute)
            and isinstance(child.value, ast.Name)
            and child.value.id == "battle"
            and child.attr == source_attr
            for child in ast.walk(node.value)
        ):
            return True
    return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    path = args.source_root.resolve() / "auto_ptu" / "rules" / "controllers" / "phase_controller.py"
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    method = find_start_round(tree)
    resetters = reset_targets(method)
    removed_effects = removed_temporary_effect_names(method)

    fixtures = [
        ("round_increment", "1" if has_round_increment(method) else "0"),
        ("trainer_actions_reset_at_round_start", "1" if "trainer" in resetters else "0"),
        ("pokemon_actions_reset_at_round_start", "1" if "mon" in resetters or "pokemon" in resetters else "0"),
        ("remove_intercept_ready", "1" if "intercept_ready" in removed_effects else "0"),
        ("remove_extra_action", "1" if "extra_action" in removed_effects else "0"),
        ("remove_delayed", "1" if "delayed" in removed_effects else "0"),
        ("remove_riposte_ready", "1" if "riposte_ready" in removed_effects else "0"),
        ("rotate_damage_last_round", "1" if assignment_uses_attribute(method, "damage_last_round", "damage_this_round") else "0"),
        ("rotate_damage_taken_from_last_round", "1" if assignment_uses_attribute(method, "damage_taken_from_last_round", "damage_taken_from") else "0"),
        ("clear_damage_this_round", "1" if clears_battle_attribute(method, "damage_this_round") else "0"),
        ("clear_damage_taken_from", "1" if clears_battle_attribute(method, "damage_taken_from") else "0"),
        ("clear_damage_received_this_round", "1" if clears_battle_attribute(method, "damage_received_this_round") else "0"),
    ]

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{name}\t{value}" for name, value in fixtures) + "\n", encoding="utf-8")
    print(f"wrote {len(fixtures)} Python round lifecycle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
