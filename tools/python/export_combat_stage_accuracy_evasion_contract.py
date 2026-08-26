#!/usr/bin/env python3
"""Freeze pinned Python contracts for Accuracy/Evasion Combat Stage state and projection."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def _is_stat_subscript(node: ast.AST) -> bool:
    if not isinstance(node, ast.Subscript):
        return False
    value = node.value
    if not isinstance(value, ast.Attribute) or value.attr != "combat_stages":
        return False
    slice_node = node.slice
    return isinstance(slice_node, ast.Name) and slice_node.id == "stat"


def _is_stat_get(node: ast.AST) -> bool:
    if not isinstance(node, ast.Call) or not node.args:
        return False
    func = node.func
    return (
        isinstance(func, ast.Attribute)
        and func.attr == "get"
        and isinstance(func.value, ast.Attribute)
        and func.value.attr == "combat_stages"
        and isinstance(node.args[0], ast.Name)
        and node.args[0].id == "stat"
    )


def _negative_six(node: ast.AST) -> bool:
    return (
        isinstance(node, ast.UnaryOp)
        and isinstance(node.op, ast.USub)
        and isinstance(node.operand, ast.Constant)
        and node.operand.value == 6
    )


def _function(tree: ast.AST, name: str) -> ast.FunctionDef | ast.AsyncFunctionDef:
    method = next(
        (
            node
            for node in ast.walk(tree)
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == name
        ),
        None,
    )
    if method is None:
        raise RuntimeError(f"{name} not found in pinned oracle")
    return method


def _constant_stage_reference(node: ast.AST, owner: str, stage: str) -> bool:
    """Match owner.combat_stages[stage] or owner.combat_stages.get(stage, ...)."""
    if isinstance(node, ast.Subscript):
        value = node.value
        slice_node = node.slice
        return (
            isinstance(value, ast.Attribute)
            and value.attr == "combat_stages"
            and isinstance(value.value, ast.Name)
            and value.value.id == owner
            and isinstance(slice_node, ast.Constant)
            and slice_node.value == stage
        )
    if isinstance(node, ast.Call) and node.args:
        func = node.func
        return (
            isinstance(func, ast.Attribute)
            and func.attr == "get"
            and isinstance(func.value, ast.Attribute)
            and func.value.attr == "combat_stages"
            and isinstance(func.value.value, ast.Name)
            and func.value.value.id == owner
            and isinstance(node.args[0], ast.Constant)
            and node.args[0].value == stage
        )
    return False


def _attribute_chain(node: ast.AST, *parts: str) -> bool:
    current = node
    for part in reversed(parts):
        if not isinstance(current, ast.Attribute) or current.attr != part:
            return False
        current = current.value
    return isinstance(current, ast.Name)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    battle_state_path = args.source_root / "auto_ptu" / "rules" / "battle_state.py"
    battle_state_source = battle_state_path.read_text(encoding="utf-8")
    battle_state_tree = ast.parse(battle_state_source)
    method = _function(battle_state_tree, "_apply_combat_stage")

    method_source = ast.get_source_segment(battle_state_source, method) or ""
    dynamic_read = any(
        (_is_stat_subscript(node) and isinstance(node.ctx, ast.Load)) or _is_stat_get(node)
        for node in ast.walk(method)
    )
    writes = [node for node in ast.walk(method) if _is_stat_subscript(node) and isinstance(node.ctx, ast.Store)]

    stat_forwarded = any(
        isinstance(node, ast.keyword)
        and node.arg == "stat"
        and isinstance(node.value, ast.Name)
        and node.value.id == "stat"
        for node in ast.walk(method)
    )
    stat_allowlist = any(
        isinstance(node, ast.Compare)
        and isinstance(node.left, ast.Name)
        and node.left.id == "stat"
        and any(isinstance(op, (ast.In, ast.NotIn)) for op in node.ops)
        and any(isinstance(comp, (ast.List, ast.Tuple, ast.Set)) for comp in node.comparators)
        for node in ast.walk(method)
    )
    has_minus_six = any(_negative_six(node) for node in ast.walk(method))
    has_plus_six = any(isinstance(node, ast.Constant) and node.value == 6 for node in ast.walk(method))
    compact_source = "".join(method_source.split())

    move_specials_source = (
        args.source_root / "auto_ptu" / "rules" / "hooks" / "move_specials.py"
    ).read_text(encoding="utf-8")

    calculations_path = args.source_root / "auto_ptu" / "rules" / "calculations.py"
    calculations_source = calculations_path.read_text(encoding="utf-8")
    calculations_tree = ast.parse(calculations_source)
    attack_hits = _function(calculations_tree, "attack_hits")
    hit_probability = _function(calculations_tree, "hit_probability")
    evasion_value = _function(calculations_tree, "evasion_value")

    accuracy_dynamic_stage = all(
        any(_constant_stage_reference(node, "attacker", "accuracy") for node in ast.walk(function))
        for function in (attack_hits, hit_probability)
    )
    accuracy_spec_cs = all(
        any(
            isinstance(node, ast.Attribute)
            and node.attr == "accuracy_cs"
            and isinstance(node.value, ast.Attribute)
            and node.value.attr == "spec"
            and isinstance(node.value.value, ast.Name)
            and node.value.value.id == "attacker"
            for node in ast.walk(function)
        )
        for function in (attack_hits, hit_probability)
    )
    accuracy_bonus = all(
        any(isinstance(node, ast.Name) and node.id == "accuracy_bonus" for node in ast.walk(function))
        for function in (attack_hits, hit_probability)
    )
    evasion_reads_evasion_stage = any(
        _constant_stage_reference(node, "pokemon", "evasion") for node in ast.walk(evasion_value)
    )
    evasion_reads_speed_stage = any(
        _constant_stage_reference(node, "pokemon", "spd") for node in ast.walk(evasion_value)
    )
    evasion_source = ast.get_source_segment(calculations_source, evasion_value) or ""
    status_only_speed_stage = (
        'pokemon.combat_stages["spd"] if category.lower() == "status" else 0' in evasion_source
    )

    method_parameters = [*method.args.posonlyargs, *method.args.args, *method.args.kwonlyargs]
    rows = {
        "stat_parameter": int(any(arg.arg == "stat" for arg in method_parameters)),
        "dynamic_stage_read": int(dynamic_read),
        "dynamic_stage_write": int(bool(writes)),
        "clamps_lower_minus_six": int(has_minus_six and "max(-6" in compact_source),
        "clamps_upper_plus_six": int(has_plus_six and "min(6" in compact_source),
        "forwards_stat_to_hook_context": int(stat_forwarded),
        "no_literal_stat_allowlist": int(not stat_allowlist),
        "parser_mentions_accuracy": int("Accuracy" in move_specials_source),
        "parser_mentions_evasion": int("Evasion" in move_specials_source),
        "accuracy_reads_dynamic_stage": int(accuracy_dynamic_stage),
        "accuracy_adds_spec_accuracy_cs": int(accuracy_spec_cs),
        "accuracy_adds_runtime_bonus": int(accuracy_bonus),
        "evasion_stage_not_projected": int(not evasion_reads_evasion_stage),
        "status_evasion_reads_speed_stage": int(evasion_reads_speed_stage and status_only_speed_stage),
    }
    expected = {key: 1 for key in rows}
    if rows != expected:
        raise RuntimeError(
            f"pinned Accuracy/Evasion Combat Stage contract changed: {rows}\n"
            f"_apply_combat_stage source:\n{method_source}\n"
            f"evasion_value source:\n{evasion_source}"
        )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "contract\tvalue\n" + "".join(f"{key}\t{value}\n" for key, value in rows.items()),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
