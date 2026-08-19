#!/usr/bin/env python3
"""Extract PhaseController and StatusController phase contracts from the pinned Python oracle."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def find_method(tree: ast.Module, class_name: str, name: str) -> ast.FunctionDef:
    for node in tree.body:
        if isinstance(node, ast.ClassDef) and node.name == class_name:
            for statement in node.body:
                if isinstance(statement, ast.FunctionDef) and statement.name == name:
                    return statement
    raise RuntimeError(f"{class_name}.{name} not found")


def find_class(tree: ast.Module, class_name: str) -> ast.ClassDef:
    for node in tree.body:
        if isinstance(node, ast.ClassDef) and node.name == class_name:
            return node
    raise RuntimeError(f"{class_name} not found")


def logs_event_type(method: ast.FunctionDef, event_type: str) -> bool:
    for node in ast.walk(method):
        if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Attribute):
            continue
        if node.func.attr != "log_event" or not node.args or not isinstance(node.args[0], ast.Dict):
            continue
        for key, value in zip(node.args[0].keys, node.args[0].values):
            if isinstance(key, ast.Constant) and key.value == "type" and isinstance(value, ast.Constant) and value.value == event_type:
                return True
    return False


def calls_attr(method: ast.FunctionDef, attr: str) -> bool:
    return any(isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr == attr for node in ast.walk(method))


def dispatches_trigger(method: ast.FunctionDef, trigger: str) -> bool:
    return any(isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr == "trigger" and node.args and isinstance(node.args[0], ast.Constant) and node.args[0].value == trigger for node in ast.walk(method))


def requires_current_actor(method: ast.FunctionDef) -> bool:
    return any(isinstance(node, ast.If) and isinstance(node.test, ast.Compare) and any(isinstance(child, ast.Attribute) and child.attr == "current_actor_id" for child in ast.walk(node.test)) and any(isinstance(child, ast.Raise) for child in ast.walk(node)) for node in method.body)


def terminal_end_return(method: ast.FunctionDef) -> bool:
    return any(isinstance(node, ast.If) and any(isinstance(child, ast.Compare) for child in ast.walk(node.test)) and any(isinstance(child, ast.Return) for child in node.body) for node in method.body)


def pending_status_skip_last_event_wins(method: ast.FunctionDef) -> bool:
    for node in ast.walk(method):
        if not isinstance(node, ast.For):
            continue
        for child in ast.walk(node):
            if not isinstance(child, ast.Assign) or not isinstance(child.value, ast.Name) or child.value.id != "payload":
                continue
            for target in child.targets:
                if isinstance(target, ast.Attribute) and target.attr == "_pending_status_skip":
                    return True
    return False


def dict_value(node: ast.Dict, key_name: str):
    for key, value in zip(node.keys, node.values):
        if isinstance(key, ast.Constant) and key.value == key_name:
            return value
    return None


def test_contains_name(test: ast.AST, name: str) -> bool:
    return any(isinstance(node, ast.Name) and node.id == name for node in ast.walk(test))


def test_contains_attr(test: ast.AST, attr: str) -> bool:
    return any(isinstance(node, ast.Attribute) and node.attr == attr for node in ast.walk(test))


def calls_method_with_string(node: ast.AST, method: str, value: str) -> bool:
    for child in ast.walk(node):
        if isinstance(child, ast.Call) and isinstance(child.func, ast.Attribute) and child.func.attr == method and child.args:
            first = child.args[0]
            if isinstance(first, ast.Constant) and first.value == value:
                return True
    return False


def reads_payload_key(node: ast.AST, key_name: str) -> bool:
    for child in ast.walk(node):
        if isinstance(child, ast.Call) and isinstance(child.func, ast.Attribute) and child.func.attr == "get" and child.args:
            first = child.args[0]
            if isinstance(first, ast.Constant) and first.value == key_name:
                return True
        if isinstance(child, ast.Subscript) and isinstance(child.slice, ast.Constant) and child.slice.value == key_name:
            return True
    return False


def flinch_payload_contract(node: ast.AST) -> tuple[bool, bool]:
    emits_flinch = False
    sets_skip = False
    for child in ast.walk(node):
        if not isinstance(child, ast.Dict):
            continue
        effect = dict_value(child, "effect")
        if not (isinstance(effect, ast.Constant) and effect.value == "flinch"):
            continue
        emits_flinch = True
        skip = dict_value(child, "skip_turn")
        if isinstance(skip, ast.Constant) and skip.value is True:
            sets_skip = True
    return emits_flinch, sets_skip


def flinch_start_contract(pokemon_state: ast.ClassDef) -> tuple[bool, bool, bool, bool, bool, bool]:
    for method in pokemon_state.body:
        if not isinstance(method, ast.FunctionDef):
            continue
        for node in ast.walk(method):
            if not isinstance(node, ast.If) or not test_contains_name(node.test, "_FLINCH_STATUS_NAMES") or not test_contains_attr(node.test, "START"):
                continue
            emits_flinch, sets_skip = flinch_payload_contract(node)
            if not (emits_flinch or sets_skip):
                continue
            reads_applied = reads_payload_key(method, "applied_round")
            expires_after_round = False
            removes_status = False
            emits_status_ends_no_skip = False
            for child in ast.walk(node):
                if isinstance(child, ast.Compare) and len(child.ops) == 1 and isinstance(child.ops[0], ast.Gt):
                    left_has_round = any(isinstance(part, ast.Attribute) and part.attr == "round" for part in ast.walk(child.left))
                    right_has_applied = any(isinstance(part, ast.Name) and part.id == "applied_round" for comp in child.comparators for part in ast.walk(comp))
                    expires_after_round |= left_has_round and right_has_applied
                if isinstance(child, ast.Call) and isinstance(child.func, ast.Attribute) and child.func.attr == "remove":
                    removes_status |= any(isinstance(part, ast.Attribute) and part.attr == "statuses" for part in ast.walk(child.func.value))
                if isinstance(child, ast.Dict):
                    effect = dict_value(child, "effect")
                    skip = dict_value(child, "skip_turn")
                    if isinstance(effect, ast.Constant) and effect.value == "status_ends" and isinstance(skip, ast.Constant) and skip.value is False:
                        emits_status_ends_no_skip = True
            print(f"Flinch START contract owner: PokemonState.{method.name}")
            return emits_flinch, sets_skip, reads_applied, expires_after_round, removes_status, emits_status_ends_no_skip
    return False, False, False, False, False, False


def strange_tempo_confusion_contract(pokemon_state: ast.ClassDef) -> tuple[bool, bool, bool]:
    for method in pokemon_state.body:
        if not isinstance(method, ast.FunctionDef):
            continue
        for node in ast.walk(method):
            if not isinstance(node, ast.If) or not test_contains_name(node.test, "_CONFUSION_STATUS_NAMES") or not test_contains_attr(node.test, "START"):
                continue
            sleeping_guard = calls_method_with_string(node, "has_status", "Sleep") and calls_method_with_string(node, "has_status", "Asleep") and calls_method_with_string(node, "has_temporary_effect", "sleep_blocked")
            for branch in ast.walk(node):
                if not isinstance(branch, ast.If) or not calls_method_with_string(branch.test, "has_ability", "Strange Tempo"):
                    continue
                emits_control = False
                sets_skip = False
                for child in ast.walk(branch):
                    if not isinstance(child, ast.Dict):
                        continue
                    ability = dict_value(child, "ability")
                    effect = dict_value(child, "effect")
                    if isinstance(ability, ast.Constant) and ability.value == "Strange Tempo" and isinstance(effect, ast.Constant) and effect.value == "confusion_control":
                        emits_control = True
                        skip = dict_value(child, "skip_turn")
                        if isinstance(skip, ast.Constant) and skip.value is True:
                            sets_skip = True
                if emits_control:
                    print(f"Strange Tempo Confusion contract owner: PokemonState.{method.name}")
                    return sleeping_guard, emits_control, not sets_skip
    return False, False, False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    source_root = args.source_root.resolve()
    controllers = source_root / "auto_ptu" / "rules" / "controllers"
    phase_tree = ast.parse((controllers / "phase_controller.py").read_text(encoding="utf-8"))
    phase_method = find_method(phase_tree, "PhaseController", "advance_phase")
    status_tree = ast.parse((controllers / "status_controller.py").read_text(encoding="utf-8"))
    status_method = find_method(status_tree, "StatusController", "run_phase_effects")
    battle_state_path = source_root / "auto_ptu" / "rules" / "battle_state.py"
    battle_state_tree = ast.parse(battle_state_path.read_text(encoding="utf-8"), filename=str(battle_state_path))
    pokemon_state = find_class(battle_state_tree, "PokemonState")
    flinch_emits, flinch_skip, flinch_reads, flinch_expires, flinch_removes, flinch_ends = flinch_start_contract(pokemon_state)
    strange_guard, strange_event, strange_no_skip = strange_tempo_confusion_contract(pokemon_state)
    fixtures = [
        ("requires_current_actor", int(requires_current_actor(phase_method))),
        ("logs_phase_event", int(logs_event_type(phase_method, "phase"))),
        ("dispatches_phase_change", int(dispatches_trigger(phase_method, "phase_change"))),
        ("runs_status_phase_effects", int(calls_attr(phase_method, "run_phase_effects"))),
        ("consumes_pending_status_skip", int(calls_attr(phase_method, "consume_pending_status_skip"))),
        ("end_phase_is_terminal", int(terminal_end_return(phase_method))),
        ("pending_status_skip_last_event_wins", int(pending_status_skip_last_event_wins(status_method))),
        ("flinch_start_emits_flinch_event", int(flinch_emits)),
        ("flinch_start_sets_skip_turn", int(flinch_skip)),
        ("flinch_phase_reads_applied_round_metadata", int(flinch_reads)),
        ("flinch_expires_when_round_advances", int(flinch_expires)),
        ("flinch_expiry_removes_status", int(flinch_removes)),
        ("flinch_expiry_emits_status_ends_without_skip", int(flinch_ends)),
        ("strange_tempo_confusion_checks_sleep_block", int(strange_guard)),
        ("strange_tempo_confusion_emits_control_event", int(strange_event)),
        ("strange_tempo_confusion_does_not_skip", int(strange_no_skip)),
    ]
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{name}\t{value}" for name, value in fixtures) + "\n", encoding="utf-8")
    print(f"wrote {len(fixtures)} Python phase lifecycle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
