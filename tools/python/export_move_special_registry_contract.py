#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def function_source(source: str, tree: ast.Module, name: str) -> str:
    for node in tree.body:
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

    path = Path(args.source_root) / "auto_ptu" / "rules" / "hooks" / "move_specials.py"
    source = path.read_text(encoding="utf-8")
    tree = ast.parse(source)
    normalize = function_source(source, tree, "_normalize_phase")
    register = function_source(source, tree, "register_move_special")
    handle = function_source(source, tree, "handle_move_specials")

    unknown_defaults = 'return "post_damage"' in normalize
    move_name_normalization = ".strip().lower()" in register

    post_marker = 'if resolved_phase == "post_damage"'
    if post_marker not in handle:
        raise RuntimeError("missing post_damage dispatch branch")
    post_block, other_block = handle.split(post_marker, 1)[1].split("else:", 1)
    specific_expr = "_MOVE_SPECIAL_HANDLERS.get(move_name, {}).get(resolved_phase, [])"
    global_expr = "_GLOBAL_MOVE_SPECIAL_HANDLERS.get(resolved_phase, [])"
    post_specific = post_block.find(specific_expr)
    post_global = post_block.find(global_expr)
    other_global = other_block.find(global_expr)
    other_specific = other_block.find(specific_expr)
    post_specific_before_global = 0 <= post_specific < post_global
    other_global_before_specific = 0 <= other_global < other_specific

    shield_dust_guard = 'has_ability("Shield Dust")' in handle
    shield_non_status = '(move.category or "").strip().lower() != "status"' in handle
    shield_post_damage = 'resolved_phase in {"post_damage", "post_result"}' in handle
    shield_returns_events = "return events" in handle
    shield_skips_non_status = all((shield_dust_guard, shield_non_status, shield_post_damage, shield_returns_events))
    shield_allows_status = shield_skips_non_status and shield_non_status

    shared_result = "result=result" in handle
    hit_snapshot = 'hit = bool(result.get("hit"))' in handle and "hit=hit" in handle

    values = [
        unknown_defaults,
        post_specific_before_global,
        other_global_before_specific,
        move_name_normalization,
        shield_skips_non_status,
        shield_allows_status,
        shared_result,
        hit_snapshot,
    ]
    if not all(values):
        raise RuntimeError(f"move-special registry contract changed: {values}")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "MOVE_SPECIAL_REGISTRY\t" + "\t".join("1" if value else "0" for value in values) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
