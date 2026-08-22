#!/usr/bin/env python3
"""Freeze Trainer Feature round-start ordering from pinned Python AutoPTU."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def phase_start_round(tree: ast.Module) -> ast.FunctionDef:
    for node in tree.body:
        if isinstance(node, ast.ClassDef) and node.name == "PhaseController":
            for statement in node.body:
                if isinstance(statement, ast.FunctionDef) and statement.name == "start_round":
                    return statement
    raise RuntimeError("PhaseController.start_round not found")


def is_battle_attr(node: ast.AST, attr: str) -> bool:
    return (
        isinstance(node, ast.Attribute)
        and isinstance(node.value, ast.Name)
        and node.value.id == "battle"
        and node.attr == attr
    )


def assignment_line(method: ast.FunctionDef, attr: str, *, empty_list: bool = False) -> int:
    for node in ast.walk(method):
        if not isinstance(node, ast.Assign):
            continue
        if not any(is_battle_attr(target, attr) for target in node.targets):
            continue
        if empty_list and not isinstance(node.value, ast.List):
            continue
        if empty_list and node.value.elts:
            continue
        return int(node.lineno)
    return -1


def call_line(method: ast.FunctionDef, method_name: str) -> int:
    lines = [
        int(node.lineno)
        for node in ast.walk(method)
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == method_name
    ]
    return min(lines) if lines else -1


def initial_sendout_call(method: ast.FunctionDef) -> ast.Call | None:
    for node in ast.walk(method):
        if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Attribute):
            continue
        if node.func.attr == "_apply_send_out_trainer_feature_effects":
            return node
    return None


def inside_round_one_guard(method: ast.FunctionDef, target_call: ast.Call) -> bool:
    for node in ast.walk(method):
        if not isinstance(node, ast.If):
            continue
        contains = any(child is target_call for child in ast.walk(node))
        if not contains:
            continue
        text = ast.unparse(node.test).replace(" ", "")
        if "battle.round==1" in text:
            return True
    return False


def sendout_guard_contract(method: ast.FunctionDef, target_call: ast.Call) -> tuple[bool, bool]:
    for node in ast.walk(method):
        if not isinstance(node, ast.If):
            continue
        if not any(child is target_call for child in ast.walk(node)):
            continue
        # Inspect the complete round-one block so the source may express fainted
        # either as a boolean attribute (`not pokemon.fainted`) or as a helper
        # (`not pokemon.is_fainted()`) without changing the frozen semantics.
        text = ast.unparse(node)
        has_active_guard = ".active" in text
        has_fainted_guard = ".fainted" in text or "is_fainted" in text
        return (has_active_guard, has_fainted_guard)
    return (False, False)


def initial_setup_true(call: ast.Call) -> bool:
    for keyword in call.keywords:
        if keyword.arg == "initial_setup" and isinstance(keyword.value, ast.Constant):
            return keyword.value.value is True
    return False


def round_start_event_line(method: ast.FunctionDef) -> int:
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
                and value.value == "round_start"
            ):
                return int(node.lineno)
    return -1


def round_start_dispatch(method: ast.FunctionDef) -> ast.Call | None:
    for node in ast.walk(method):
        if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Attribute):
            continue
        if node.func.attr != "trigger" or not node.args:
            continue
        if isinstance(node.args[0], ast.Constant) and node.args[0].value == "round_start":
            return node
    return None


def payload_uses_round(call: ast.Call) -> bool:
    for keyword in call.keywords:
        if keyword.arg != "payload" or not isinstance(keyword.value, ast.Dict):
            continue
        for key, value in zip(keyword.value.keys, keyword.value.values):
            if (
                isinstance(key, ast.Constant)
                and key.value == "round"
                and is_battle_attr(value, "round")
            ):
                return True
    return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_root / "auto_ptu" / "rules" / "controllers" / "phase_controller.py"
    method = phase_start_round(ast.parse(source.read_text(encoding="utf-8"), filename=str(source)))

    declared_line = assignment_line(method, "declared_actions", empty_list=True)
    trainer_reset_line = call_line(method, "reset_actions")
    sendout = initial_sendout_call(method)
    if sendout is None:
        raise RuntimeError("initial send-out Trainer Feature call not found")
    sendout_line = int(sendout.lineno)
    active_guard, fainted_guard = sendout_guard_contract(method, sendout)
    initiative_line = assignment_line(method, "initiative_order")
    event_line = round_start_event_line(method)
    dispatch = round_start_dispatch(method)
    if dispatch is None:
        raise RuntimeError("round_start Trainer Feature dispatch not found")
    dispatch_line = int(dispatch.lineno)

    rows = {
        "clears_declared_actions": declared_line >= 0,
        "declared_actions_after_trainer_reset": trainer_reset_line >= 0 and declared_line > trainer_reset_line,
        "declared_actions_before_initial_sendout": declared_line >= 0 and declared_line < sendout_line,
        "initial_sendout_round_one_only": inside_round_one_guard(method, sendout),
        "initial_sendout_requires_active_pokemon": active_guard,
        "initial_sendout_skips_fainted_pokemon": fainted_guard,
        "initial_sendout_uses_initial_setup": initial_setup_true(sendout),
        "initiative_rebuild_before_round_start_event": initiative_line >= 0 and event_line > initiative_line,
        "round_start_event_before_feature_dispatch": event_line >= 0 and dispatch_line > event_line,
        "dispatches_round_start_trigger": dispatch_line >= 0,
        "round_start_payload_uses_current_round": payload_uses_round(dispatch),
    }

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{key}\t{1 if value else 0}" for key, value in rows.items()) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} Trainer round-feature contract fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
