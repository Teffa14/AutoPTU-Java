#!/usr/bin/env python3
"""Freeze the pinned Python contract that makes Accuracy/Evasion generic Combat Stages."""
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


def _negative_six(node: ast.AST) -> bool:
    return (
        isinstance(node, ast.UnaryOp)
        and isinstance(node.op, ast.USub)
        and isinstance(node.operand, ast.Constant)
        and node.operand.value == 6
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_path = args.source_root / "auto_ptu" / "rules" / "battle_state.py"
    source = source_path.read_text(encoding="utf-8")
    tree = ast.parse(source)
    method = next(
        (
            node
            for node in ast.walk(tree)
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
            and node.name == "_apply_combat_stage"
        ),
        None,
    )
    if method is None:
        raise RuntimeError("_apply_combat_stage not found in pinned oracle")

    method_source = ast.get_source_segment(source, method) or ""
    reads = [node for node in ast.walk(method) if _is_stat_subscript(node) and isinstance(node.ctx, ast.Load)]
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

    # The generic move-special parser already emits these two names into _apply_combat_stage.
    move_specials_source = (
        args.source_root / "auto_ptu" / "rules" / "hooks" / "move_specials.py"
    ).read_text(encoding="utf-8")

    rows = {
        "stat_parameter": int(any(arg.arg == "stat" for arg in method.args.args)),
        "dynamic_stage_read": int(bool(reads)),
        "dynamic_stage_write": int(bool(writes)),
        "clamps_lower_minus_six": int(has_minus_six and "max(-6" in method_source.replace(" ", "")),
        "clamps_upper_plus_six": int(has_plus_six and "min(6" in method_source.replace(" ", "")),
        "forwards_stat_to_hook_context": int(stat_forwarded),
        "no_literal_stat_allowlist": int(not stat_allowlist),
        "parser_mentions_accuracy": int("Accuracy" in move_specials_source),
        "parser_mentions_evasion": int("Evasion" in move_specials_source),
    }
    expected = {key: 1 for key in rows}
    if rows != expected:
        raise RuntimeError(
            f"pinned Accuracy/Evasion Combat Stage contract changed: {rows}\n"
            f"_apply_combat_stage source:\n{method_source}"
        )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "contract\tvalue\n" + "".join(f"{key}\t{value}\n" for key, value in rows.items()),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
