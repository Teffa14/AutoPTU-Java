#!/usr/bin/env python3
"""Freeze Quick Switch entry/trigger semantics from the pinned Python oracle."""
from __future__ import annotations

import argparse
import ast
import inspect
import sys
import textwrap
from pathlib import Path


def _function_ast(callable_obj) -> ast.FunctionDef | ast.AsyncFunctionDef:
    tree = ast.parse(textwrap.dedent(inspect.getsource(callable_obj)))
    function = tree.body[0]
    if not isinstance(function, (ast.FunctionDef, ast.AsyncFunctionDef)):
        raise AssertionError("oracle source did not parse as a function")
    return function


def _expr(node: ast.AST | None) -> str:
    return "<missing>" if node is None else ast.unparse(node)


def _calls(function: ast.AST, suffix: str) -> list[ast.Call]:
    return [node for node in ast.walk(function)
            if isinstance(node, ast.Call) and _expr(node.func).endswith(suffix)]


def _single_call(function: ast.AST, suffix: str) -> ast.Call:
    calls = _calls(function, suffix)
    if len(calls) != 1:
        raise AssertionError(f"expected exactly one {suffix} call, got {len(calls)}")
    return calls[0]


def _keywords(call: ast.Call) -> dict[str, str]:
    return {keyword.arg: _expr(keyword.value) for keyword in call.keywords if keyword.arg is not None}


def _assignment(function: ast.AST, name: str) -> str:
    for node in ast.walk(function):
        if not isinstance(node, (ast.Assign, ast.AnnAssign)):
            continue
        targets = node.targets if isinstance(node, ast.Assign) else [node.target]
        if any(isinstance(target, ast.Name) and target.id == name for target in targets):
            return _expr(node.value)
    return "<missing>"


def _assert_contains(source: str, fragments: list[str], label: str) -> None:
    missing = [fragment for fragment in fragments if fragment not in source]
    if missing:
        raise AssertionError(f"pinned Quick Switch {label} contract changed; missing {missing!r}")


def _assert_ordered_contains(source: str, fragments: list[str], label: str) -> None:
    """Freeze relative source order for side effects whose ordering is parity-sensitive."""
    cursor = -1
    for fragment in fragments:
        position = source.find(fragment, cursor + 1)
        if position < 0:
            raise AssertionError(
                f"pinned Quick Switch {label} ordering changed; missing or reordered {fragment!r}"
            )
        cursor = position


def _write_row(handle, *parts: object) -> None:
    handle.write("\t".join(str(part) for part in parts) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.rules.battle_state import BattleState, QuickSwitchAction

    validate = _function_ast(QuickSwitchAction.validate)
    resolve = _function_ast(QuickSwitchAction.resolve)
    trigger = _function_ast(BattleState._maybe_trigger_quick_switch)
    faint_trigger = _function_ast(BattleState._handle_quick_switch_faint_trigger)
    apply_switch = _function_ast(BattleState._apply_switch)

    validate_source = ast.unparse(validate)
    resolve_source = ast.unparse(resolve)
    trigger_source = ast.unparse(trigger)
    faint_source = ast.unparse(faint_trigger)
    apply_switch_source = ast.unparse(apply_switch)

    expected_action_ap = "1 if actor.has_trainer_feature('Juggler') else 2"
    action_validate_ap = _assignment(validate, "ap_cost")
    action_resolve_ap = _assignment(resolve, "ap_cost")
    if action_validate_ap != expected_action_ap or action_resolve_ap != expected_action_ap:
        raise AssertionError(
            "pinned QuickSwitchAction AP policy changed: "
            f"validate={action_validate_ap!r}, resolve={action_resolve_ap!r}"
        )

    _assert_contains(validate_source, [
        "trainer.available_ap(battle.round) < ap_cost",
        "not actor.active",
        "actor.fainted",
        "replacement.controller_id != actor.controller_id",
        "replacement.active",
        "replacement.fainted",
    ], "manual validation")

    action_switch = _keywords(_single_call(resolve, "_apply_switch"))
    expected_action_switch = {
        "outgoing_id": "self.actor_id",
        "replacement_id": "self.replacement_id",
        "initiator_id": "trainer.identifier",
        "apply_tag_in": "True",
        "allow_replacement_turn": "True",
        "allow_immediate": "False",
        "allow_quick_switch_triggers": "False",
    }
    if action_switch != expected_action_switch:
        raise AssertionError(f"pinned QuickSwitchAction switch policy changed: {action_switch!r}")
    _assert_contains(resolve_source, [
        "trainer.consume_ap(ap_cost)",
        "replacement.add_temporary_effect('quick_switch_sent_out', round=battle.round, expires_round=battle.round)",
        "'type': 'trainer_feature'",
        "'feature': 'Quick Switch'",
        "'effect': 'switch'",
        "'ap_cost': 2",
    ], "manual resolve")
    _assert_ordered_contains(resolve_source, [
        "trainer.consume_ap(ap_cost)",
        "battle._apply_switch(",
        "replacement.add_temporary_effect('quick_switch_sent_out'",
        "'type': 'trainer_feature'",
    ], "manual side effects")

    _assert_contains(trigger_source, [
        "actor is None",
        "actor.fainted",
        "not actor.active",
        "not actor.has_trainer_feature('Quick Switch')",
        "int(getattr(trainer, 'ap', 0) or 0) < 2",
        "replacements = self._quick_switch_replacements(actor_id)",
        "response = self.prompt_response(actor_id, prompt)",
        "choice_id = replacements[0]",
        "trainer.consume_ap(2)",
        "replacement.add_temporary_effect('quick_switch_sent_out', round=self.round, expires_round=self.round)",
        "'type': 'trainer_feature'",
        "'feature': 'Quick Switch'",
        "'effect': 'switch'",
        "'trigger': trigger",
        "'ap_cost': 2",
    ], "trigger")
    _assert_ordered_contains(trigger_source, [
        "trainer.consume_ap(2)",
        "self._apply_switch(",
        "replacement.add_temporary_effect('quick_switch_sent_out'",
        "'type': 'trainer_feature'",
    ], "trigger side effects")

    trigger_switch = _keywords(_single_call(trigger, "_apply_switch"))
    expected_trigger_switch = {
        "outgoing_id": "actor_id",
        "replacement_id": "choice_id",
        "initiator_id": "trainer.identifier",
        "allow_replacement_turn": "True",
        "allow_immediate": "False",
        "allow_quick_switch_triggers": "False",
    }
    if trigger_switch != expected_trigger_switch:
        raise AssertionError(f"pinned triggered Quick Switch policy changed: {trigger_switch!r}")

    _assert_contains(trigger_source, [
        "'label': 'Quick Switch?'",
        "'phase': 'interrupt'",
        "'optional': True",
        "'feature': 'Quick Switch'",
        "'ap_cost': 2",
        "'kind': 'choice'",
    ], "prompt")
    _assert_contains(faint_source, [
        "target.get_temporary_effects('quick_switch_faint_handled')",
        "target.add_temporary_effect('quick_switch_faint_handled'",
        "trigger='ally_faint'",
    ], "faint trigger")
    _assert_contains(apply_switch_source, [
        "trigger='opponent_send_out'",
        "trigger_target_id=replacement_id",
    ], "opponent send-out trigger")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        _write_row(handle, "ACTION_AP", action_resolve_ap)
        _write_row(handle, "ACTION_SWITCH", *(f"{key}={value}" for key, value in expected_action_switch.items()))
        _write_row(handle, "ACTION_TEMP", "quick_switch_sent_out", "round=current", "expires=current")
        _write_row(handle, "ACTION_EVENT", "type=trainer_feature", "feature=Quick Switch", "effect=switch", "ap_cost=2")
        _write_row(handle, "ACTION_ORDER", "consume_ap", "apply_switch", "quick_switch_sent_out", "trainer_feature_event")
        _write_row(handle, "TRIGGER_AP", "required>=2", "consume=2")
        _write_row(handle, "TRIGGER_PROMPT", "phase=interrupt", "optional=True", "default=first_replacement")
        _write_row(handle, "TRIGGER_SWITCH", *(f"{key}={value}" for key, value in expected_trigger_switch.items()))
        _write_row(handle, "TRIGGER_TEMP", "quick_switch_sent_out", "round=current", "expires=current")
        _write_row(handle, "TRIGGER_EVENT", "type=trainer_feature", "feature=Quick Switch", "effect=switch", "trigger=propagated", "ap_cost=2")
        _write_row(handle, "TRIGGER_ORDER", "consume_ap", "apply_switch", "quick_switch_sent_out", "trainer_feature_event")
        _write_row(handle, "TRIGGER_SOURCE", "opponent_send_out", "ally_faint")
        _write_row(handle, "FAINT_GUARD", "quick_switch_faint_handled", "round=current", "expires=current")
    print(output)


if __name__ == "__main__":
    main()
