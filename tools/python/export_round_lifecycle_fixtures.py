#!/usr/bin/env python3
"""Extract round-start and turn-end lifecycle contracts from Python PhaseController."""
from __future__ import annotations

import argparse
import ast
import importlib
import sys
from pathlib import Path


def find_phase_method(tree: ast.Module, name: str) -> ast.FunctionDef:
    for node in tree.body:
        if isinstance(node, ast.ClassDef) and node.name == "PhaseController":
            for statement in node.body:
                if isinstance(statement, ast.FunctionDef) and statement.name == name:
                    return statement
    raise RuntimeError(f"PhaseController.{name} not found")


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


def calls_method_on_name(method: ast.FunctionDef, receiver_name: str, method_name: str) -> bool:
    return any(
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == method_name
        and isinstance(node.func.value, ast.Name)
        and node.func.value.id == receiver_name
        for node in ast.walk(method)
    )


def method_call_line(method: ast.FunctionDef, receiver_name: str, method_name: str) -> int:
    lines = [
        int(node.lineno)
        for node in ast.walk(method)
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == method_name
        and isinstance(node.func.value, ast.Name)
        and node.func.value.id == receiver_name
    ]
    return min(lines) if lines else -1


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


def adds_temporary_effect(method: ast.FunctionDef, effect_name: str) -> bool:
    for node in ast.walk(method):
        if not isinstance(node, ast.Call):
            continue
        if not isinstance(node.func, ast.Attribute) or node.func.attr != "add_temporary_effect":
            continue
        if not node.args:
            continue
        first = node.args[0]
        if isinstance(first, ast.Constant) and first.value == effect_name:
            return True
    return False


def temporary_effect_uses_round_payload(method: ast.FunctionDef, effect_name: str) -> bool:
    for node in ast.walk(method):
        if not isinstance(node, ast.Call):
            continue
        if not isinstance(node.func, ast.Attribute) or node.func.attr != "add_temporary_effect":
            continue
        if not node.args or not isinstance(node.args[0], ast.Constant) or node.args[0].value != effect_name:
            continue
        for keyword in node.keywords:
            if keyword.arg != "round":
                continue
            if (
                isinstance(keyword.value, ast.Attribute)
                and isinstance(keyword.value.value, ast.Name)
                and keyword.value.value.id == "battle"
                and keyword.value.attr == "round"
            ):
                return True
    return False


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


def assignment_uses_any_attribute(method: ast.FunctionDef, target_attr: str, source_attr: str) -> bool:
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
            isinstance(child, ast.Attribute) and child.attr == source_attr
            for child in ast.walk(node.value)
        ):
            return True
    return False


def logs_event_type(method: ast.FunctionDef, event_type: str) -> bool:
    for node in ast.walk(method):
        if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Attribute):
            continue
        if node.func.attr != "log_event" or not node.args or not isinstance(node.args[0], ast.Dict):
            continue
        data = node.args[0]
        for key, value in zip(data.keys, data.values):
            if (
                isinstance(key, ast.Constant)
                and key.value == "type"
                and isinstance(value, ast.Constant)
                and value.value == event_type
            ):
                return True
    return False


def dispatches_trigger(method: ast.FunctionDef, trigger_name: str) -> bool:
    return any(
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == "trigger"
        and node.args
        and isinstance(node.args[0], ast.Constant)
        and node.args[0].value == trigger_name
        for node in ast.walk(method)
    )


def assigns_battle_attribute_none(method: ast.FunctionDef, attribute: str) -> bool:
    for node in ast.walk(method):
        if not isinstance(node, ast.Assign):
            continue
        if not isinstance(node.value, ast.Constant) or node.value.value is not None:
            continue
        for target in node.targets:
            if (
                isinstance(target, ast.Attribute)
                and isinstance(target.value, ast.Name)
                and target.value.id == "battle"
                and target.attr == attribute
            ):
                return True
    return False


def assigns_phase_start(method: ast.FunctionDef) -> bool:
    for node in ast.walk(method):
        if not isinstance(node, ast.Assign):
            continue
        if not any(
            isinstance(target, ast.Attribute)
            and isinstance(target.value, ast.Name)
            and target.value.id == "battle"
            and target.attr == "phase"
            for target in node.targets
        ):
            continue
        if (
            isinstance(node.value, ast.Attribute)
            and isinstance(node.value.value, ast.Name)
            and node.value.value.id == "TurnPhase"
            and node.value.attr == "START"
        ):
            return True
    return False


def trainer_ap_behavior(source_root: Path) -> dict[str, int]:
    sys.path.insert(0, str(source_root))
    battle_state = importlib.import_module("auto_ptu.rules.battle_state")
    TrainerState = battle_state.TrainerState
    ActionType = battle_state.ActionType

    trainer = TrainerState(identifier="trainer", name="Trainer")
    trainer.ap = 4
    trainer.grant_temporary_ap(3, expires_round=4, source="  round bonus  ")
    same_round_expired = trainer.expire_temporary_ap(4)
    same_round_ap = int(trainer.ap)
    same_round_grants = len(trainer.temporary_ap)
    next_round_expired = trainer.expire_temporary_ap(5)
    next_round_ap = int(trainer.ap)
    next_round_grants = len(trainer.temporary_ap)

    spent = TrainerState(identifier="spent", name="Spent")
    spent.ap = 4
    spent.grant_temporary_ap(3, expires_round=4, source="temporary")
    spent.consume_ap(2)
    spend_ap = int(spent.ap)
    spend_grant_amount = int(spent.temporary_ap[0]["amount"]) if spent.temporary_ap else 0
    expire_after_spend = spent.expire_temporary_ap(5)
    final_ap = int(spent.ap)

    reset = TrainerState(identifier="reset", name="Reset")
    reset.mark_action(ActionType.STANDARD, "used")
    reset.mark_action(ActionType.SHIFT, "used")
    reset.reset_actions()

    return {
        "trainer_temp_ap_same_round_expired": same_round_expired,
        "trainer_temp_ap_same_round_ap": same_round_ap,
        "trainer_temp_ap_same_round_grants": same_round_grants,
        "trainer_temp_ap_next_round_expired": next_round_expired,
        "trainer_temp_ap_next_round_ap": next_round_ap,
        "trainer_temp_ap_next_round_grants": next_round_grants,
        "trainer_temp_ap_spend_ap": spend_ap,
        "trainer_temp_ap_spend_remaining_grant": spend_grant_amount,
        "trainer_temp_ap_expire_after_spend": expire_after_spend,
        "trainer_temp_ap_final_ap": final_ap,
        "trainer_reset_actions_empty": 1 if not reset.actions_taken else 0,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    path = source_root / "auto_ptu" / "rules" / "controllers" / "phase_controller.py"
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    start_round = find_phase_method(tree, "start_round")
    end_turn = find_phase_method(tree, "end_turn")
    resetters = reset_targets(start_round)
    round_removed_effects = removed_temporary_effect_names(start_round)
    turn_removed_effects = removed_temporary_effect_names(end_turn)
    expire_line = method_call_line(start_round, "trainer", "expire_temporary_ap")
    reset_line = method_call_line(start_round, "trainer", "reset_actions")

    fixtures = [
        ("round_increment", "1" if has_round_increment(start_round) else "0"),
        ("trainer_actions_reset_at_round_start", "1" if "trainer" in resetters else "0"),
        ("trainer_temporary_ap_expires_at_round_start", "1" if calls_method_on_name(start_round, "trainer", "expire_temporary_ap") else "0"),
        ("trainer_ap_expiry_before_action_reset", "1" if expire_line >= 0 and reset_line >= 0 and expire_line < reset_line else "0"),
        ("pokemon_actions_reset_at_round_start", "1" if "mon" in resetters or "pokemon" in resetters else "0"),
        ("remove_intercept_ready", "1" if "intercept_ready" in round_removed_effects else "0"),
        ("remove_extra_action", "1" if "extra_action" in round_removed_effects else "0"),
        ("remove_delayed", "1" if "delayed" in round_removed_effects else "0"),
        ("remove_riposte_ready", "1" if "riposte_ready" in round_removed_effects else "0"),
        ("rotate_damage_last_round", "1" if assignment_uses_attribute(start_round, "damage_last_round", "damage_this_round") else "0"),
        ("rotate_damage_taken_from_last_round", "1" if assignment_uses_attribute(start_round, "damage_taken_from_last_round", "damage_taken_from") else "0"),
        ("clear_damage_this_round", "1" if clears_battle_attribute(start_round, "damage_this_round") else "0"),
        ("clear_damage_taken_from", "1" if clears_battle_attribute(start_round, "damage_taken_from") else "0"),
        ("clear_damage_received_this_round", "1" if clears_battle_attribute(start_round, "damage_received_this_round") else "0"),
        ("rotate_injuries_previous_round", "1" if assignment_uses_attribute(start_round, "_injuries_previous_round", "_injuries_last_round") else "0"),
        ("snapshot_injuries_last_round", "1" if assignment_uses_any_attribute(start_round, "_injuries_last_round", "injuries") else "0"),
        ("turn_end_remove_extra_action", "1" if "extra_action" in turn_removed_effects else "0"),
        ("turn_end_remove_last_turn_round", "1" if "last_turn_round" in turn_removed_effects else "0"),
        ("turn_end_add_last_turn_round", "1" if adds_temporary_effect(end_turn, "last_turn_round") else "0"),
        ("turn_end_last_turn_round_uses_round", "1" if temporary_effect_uses_round_payload(end_turn, "last_turn_round") else "0"),
        ("turn_end_logs_event", "1" if logs_event_type(end_turn, "turn_end") else "0"),
        ("turn_end_dispatches_trainer_features", "1" if dispatches_trigger(end_turn, "turn_end") else "0"),
        ("turn_end_clears_current_actor", "1" if assigns_battle_attribute_none(end_turn, "current_actor_id") else "0"),
        ("turn_end_resets_phase_start", "1" if assigns_phase_start(end_turn) else "0"),
    ]
    fixtures.extend((name, str(value)) for name, value in trainer_ap_behavior(source_root).items())

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{name}\t{value}" for name, value in fixtures) + "\n", encoding="utf-8")
    print(f"wrote {len(fixtures)} Python lifecycle oracle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
