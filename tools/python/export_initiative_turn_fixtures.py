#!/usr/bin/env python3
"""Extract the authoritative current-round initiative-turn contract from Python BattleState."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def find_method(tree: ast.AST, name: str) -> ast.FunctionDef:
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef) and node.name == name:
            return node
    raise RuntimeError(f"{name} not found")


def has_augmented_index(method: ast.FunctionDef) -> bool:
    return any(
        isinstance(node, ast.AugAssign)
        and isinstance(node.target, ast.Attribute)
        and node.target.attr == "_initiative_index"
        and isinstance(node.op, ast.Add)
        and isinstance(node.value, ast.Constant)
        and node.value.value == 1
        for node in ast.walk(method)
    )


def calls(method: ast.FunctionDef, name: str) -> bool:
    return any(
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == name
        for node in ast.walk(method)
    )


def call_line(method: ast.FunctionDef, name: str) -> int | None:
    lines = [
        node.lineno
        for node in ast.walk(method)
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == name
    ]
    return min(lines) if lines else None


def reads_attribute(method: ast.FunctionDef, name: str) -> bool:
    return any(isinstance(node, ast.Attribute) and node.attr == name for node in ast.walk(method))


def assigns_attribute(method: ast.FunctionDef, name: str) -> bool:
    for node in ast.walk(method):
        if not isinstance(node, (ast.Assign, ast.AnnAssign)):
            continue
        targets = node.targets if isinstance(node, ast.Assign) else [node.target]
        if any(isinstance(target, ast.Attribute) and target.attr == name for target in targets):
            return True
    return False


def assigns_phase_start(method: ast.FunctionDef) -> bool:
    for node in ast.walk(method):
        if not isinstance(node, ast.Assign):
            continue
        if not any(isinstance(target, ast.Attribute) and target.attr == "phase" for target in node.targets):
            continue
        value = node.value
        if (
            isinstance(value, ast.Attribute)
            and isinstance(value.value, ast.Name)
            and value.value.id == "TurnPhase"
            and value.attr == "START"
        ):
            return True
    return False


def log_event_line(method: ast.FunctionDef, event_type: str) -> int | None:
    lines: list[int] = []
    for node in ast.walk(method):
        if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Attribute):
            continue
        if node.func.attr != "log_event" or not node.args or not isinstance(node.args[0], ast.Dict):
            continue
        for key, value in zip(node.args[0].keys, node.args[0].values):
            if (
                isinstance(key, ast.Constant)
                and key.value == "type"
                and isinstance(value, ast.Constant)
                and value.value == event_type
            ):
                lines.append(node.lineno)
    return min(lines) if lines else None


def logs_event_type(method: ast.FunctionDef, event_type: str) -> bool:
    return log_event_line(method, event_type) is not None


def ordered(*lines: int | None) -> bool:
    return all(line is not None for line in lines) and list(lines) == sorted(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_root.resolve() / "auto_ptu" / "rules" / "battle_state.py"
    tree = ast.parse(source.read_text(encoding="utf-8"), filename=str(source))
    method = find_method(tree, "advance_turn")

    turn_start_line = log_event_line(method, "turn_start")
    start_effects_line = call_line(method, "_run_phase_effects")
    pending_skip_line = call_line(method, "_consume_pending_status_skip")

    fixtures = [
        ("increments_cursor_before_selection", has_augmented_index(method)),
        ("rollover_calls_start_round", calls(method, "start_round")),
        ("reads_initiative_order", reads_attribute(method, "initiative_order")),
        ("guards_active_state", reads_attribute(method, "active")),
        ("guards_fainted_state", reads_attribute(method, "fainted")),
        ("resets_selected_actor_actions", calls(method, "reset_actions")),
        ("assigns_current_actor", assigns_attribute(method, "current_actor_id")),
        ("sets_start_phase", assigns_phase_start(method)),
        ("logs_turn_start", logs_event_type(method, "turn_start")),
        ("runs_start_phase_effects", calls(method, "_run_phase_effects")),
        ("consumes_pending_status_skip", calls(method, "_consume_pending_status_skip")),
        ("turn_start_precedes_start_effects", ordered(turn_start_line, start_effects_line)),
        ("start_effects_precede_pending_skip", ordered(start_effects_line, pending_skip_line)),
    ]

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(f"{name}\t{1 if value else 0}" for name, value in fixtures) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(fixtures)} Python initiative-turn oracle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
