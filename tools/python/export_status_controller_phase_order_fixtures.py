#!/usr/bin/env python3
"""Freeze StatusController phase-envelope ordering from the pinned Python oracle."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def find_method(tree: ast.Module, class_name: str, method_name: str) -> ast.FunctionDef:
    for node in tree.body:
        if not isinstance(node, ast.ClassDef) or node.name != class_name:
            continue
        for child in node.body:
            if isinstance(child, ast.FunctionDef) and child.name == method_name:
                return child
    raise RuntimeError(f"{class_name}.{method_name} not found")


def call_lines(method: ast.FunctionDef, attr: str) -> list[int]:
    lines: list[int] = []
    for node in ast.walk(method):
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr == attr:
            lines.append(node.lineno)
    return sorted(lines)


def first_call(method: ast.FunctionDef, attr: str) -> int:
    lines = call_lines(method, attr)
    if not lines:
        raise RuntimeError(f"call {attr} not found")
    return lines[0]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_root.resolve() / "auto_ptu" / "rules" / "controllers" / "status_controller.py"
    tree = ast.parse(source.read_text(encoding="utf-8"), filename=str(source))
    method = find_method(tree, "StatusController", "run_phase_effects")

    held_start = first_call(method, "_apply_held_item_start")
    food_regen = first_call(method, "_apply_food_regen")
    food_buff = first_call(method, "_apply_food_buff_start")
    combatant = first_call(method, "handle_phase_effects")
    held_end = first_call(method, "_apply_held_item_end")

    fixtures = [
        ("start_held_item_before_food_regen", int(held_start < food_regen)),
        ("start_food_regen_before_food_buff", int(food_regen < food_buff)),
        ("start_food_buff_before_combatant", int(food_buff < combatant)),
        ("end_combatant_before_held_item", int(combatant < held_end)),
        ("non_start_end_combatant_still_present", int(bool(call_lines(method, "handle_phase_effects")))),
    ]

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{name}\t{value}" for name, value in fixtures) + "\n", encoding="utf-8")
    print(f"StatusController ordering lines: held_start={held_start}, food_regen={food_regen}, food_buff={food_buff}, combatant={combatant}, held_end={held_end}")
    print(f"wrote {len(fixtures)} StatusController phase-order fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
