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
    return any(
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == attr
        for node in ast.walk(method)
    )


def dispatches_trigger(method: ast.FunctionDef, trigger: str) -> bool:
    return any(
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == "trigger"
        and node.args
        and isinstance(node.args[0], ast.Constant)
        and node.args[0].value == trigger
        for node in ast.walk(method)
    )


def requires_current_actor(method: ast.FunctionDef) -> bool:
    return any(
        isinstance(node, ast.If)
        and isinstance(node.test, ast.Compare)
        and any(isinstance(child, ast.Attribute) and child.attr == "current_actor_id" for child in ast.walk(node.test))
        and any(isinstance(child, ast.Raise) for child in ast.walk(node))
        for node in method.body
    )


def terminal_end_return(method: ast.FunctionDef) -> bool:
    return any(
        isinstance(node, ast.If)
        and any(isinstance(child, ast.Compare) for child in ast.walk(node.test))
        and any(isinstance(child, ast.Return) for child in node.body)
        for node in method.body
    )


def pending_status_skip_last_event_wins(method: ast.FunctionDef) -> bool:
    """True when ordered phase events overwrite the single pending-skip slot in-loop."""
    for node in ast.walk(method):
        if not isinstance(node, ast.For):
            continue
        for child in ast.walk(node):
            if not isinstance(child, ast.Assign):
                continue
            if not isinstance(child.value, ast.Name) or child.value.id != "payload":
                continue
            for target in child.targets:
                if isinstance(target, ast.Attribute) and target.attr == "_pending_status_skip":
                    return True
    return False


def method_source(source: str, method: ast.FunctionDef) -> str:
    lines = source.splitlines()
    end_lineno = method.end_lineno or method.lineno
    return "\n".join(lines[method.lineno - 1:end_lineno])


def flinch_start_contract(source: str, method: ast.FunctionDef) -> tuple[bool, bool]:
    """Freeze the concrete Flinch START payload inside PokemonState.handle_phase_effects.

    This intentionally scopes the textual check to the located method. The Python
    oracle file is very large and this contract is about stable literal payload
    fields, so source-level matching is less brittle here than correlating nested
    AST nodes across the entire method.
    """
    text = method_source(source, method)
    compact = " ".join(text.split())
    has_guard = "if name in _FLINCH_STATUS_NAMES and phase == TurnPhase.START:" in compact
    emits_flinch = '"effect": "flinch"' in text
    sets_skip = '"skip_turn": True' in text
    return has_guard and emits_flinch, has_guard and emits_flinch and sets_skip


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    controllers = source_root / "auto_ptu" / "rules" / "controllers"
    phase_path = controllers / "phase_controller.py"
    phase_tree = ast.parse(phase_path.read_text(encoding="utf-8"), filename=str(phase_path))
    phase_method = find_method(phase_tree, "PhaseController", "advance_phase")

    status_path = controllers / "status_controller.py"
    status_tree = ast.parse(status_path.read_text(encoding="utf-8"), filename=str(status_path))
    status_method = find_method(status_tree, "StatusController", "run_phase_effects")

    battle_state_path = source_root / "auto_ptu" / "rules" / "battle_state.py"
    battle_state_source = battle_state_path.read_text(encoding="utf-8")
    battle_state_tree = ast.parse(battle_state_source, filename=str(battle_state_path))
    pokemon_phase_method = find_method(battle_state_tree, "PokemonState", "handle_phase_effects")
    flinch_emits_event, flinch_sets_skip = flinch_start_contract(battle_state_source, pokemon_phase_method)

    fixtures = [
        ("requires_current_actor", int(requires_current_actor(phase_method))),
        ("logs_phase_event", int(logs_event_type(phase_method, "phase"))),
        ("dispatches_phase_change", int(dispatches_trigger(phase_method, "phase_change"))),
        ("runs_status_phase_effects", int(calls_attr(phase_method, "run_phase_effects"))),
        ("consumes_pending_status_skip", int(calls_attr(phase_method, "consume_pending_status_skip"))),
        ("end_phase_is_terminal", int(terminal_end_return(phase_method))),
        ("pending_status_skip_last_event_wins", int(pending_status_skip_last_event_wins(status_method))),
        ("flinch_start_emits_flinch_event", int(flinch_emits_event)),
        ("flinch_start_sets_skip_turn", int(flinch_sets_skip)),
    ]

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{name}\t{value}" for name, value in fixtures) + "\n", encoding="utf-8")
    print(f"wrote {len(fixtures)} Python phase lifecycle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
