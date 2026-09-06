#!/usr/bin/env python3
"""Freeze Python start_round ordering for initiative rebuild and round histories."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def start_round_method(tree: ast.Module) -> ast.FunctionDef:
    for node in tree.body:
        if isinstance(node, ast.ClassDef) and node.name == "PhaseController":
            for statement in node.body:
                if isinstance(statement, ast.FunctionDef) and statement.name == "start_round":
                    return statement
    raise RuntimeError("PhaseController.start_round not found")


def call_line(method: ast.FunctionDef, name: str) -> int:
    lines = [
        node.lineno
        for node in ast.walk(method)
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == name
    ]
    if not lines:
        raise RuntimeError(f"call {name} not found")
    return min(lines)


def assignment_line(method: ast.FunctionDef, attribute: str) -> int:
    for node in ast.walk(method):
        if not isinstance(node, ast.Assign):
            continue
        for target in node.targets:
            if (
                isinstance(target, ast.Attribute)
                and isinstance(target.value, ast.Name)
                and target.value.id == "battle"
                and target.attr == attribute
            ):
                return node.lineno
    raise RuntimeError(f"assignment battle.{attribute} not found")


def clear_line(method: ast.FunctionDef, attribute: str) -> int:
    for node in ast.walk(method):
        if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Attribute):
            continue
        if node.func.attr != "clear" or not isinstance(node.func.value, ast.Attribute):
            continue
        target = node.func.value
        if (
            isinstance(target.value, ast.Name)
            and target.value.id == "battle"
            and target.attr == attribute
        ):
            return node.lineno
    raise RuntimeError(f"clear battle.{attribute} not found")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    phase_path = (
        args.source_root.resolve()
        / "auto_ptu"
        / "rules"
        / "controllers"
        / "phase_controller.py"
    )
    method = start_round_method(ast.parse(phase_path.read_text(encoding="utf-8")))

    ordered = sorted(
        [
            (call_line(method, "_build_initiative_order"), "initiative_rebuild"),
            (assignment_line(method, "damage_last_round"), "damage_last_round"),
            (assignment_line(method, "damage_taken_from_last_round"), "damage_taken_from_last_round"),
            (clear_line(method, "damage_this_round"), "clear_damage_this_round"),
            (clear_line(method, "damage_taken_from"), "clear_damage_taken_from"),
            (clear_line(method, "damage_received_this_round"), "clear_damage_received_this_round"),
            (assignment_line(method, "_injuries_previous_round"), "injuries_previous_round"),
            (assignment_line(method, "_injuries_last_round"), "injuries_last_round"),
        ],
        key=lambda entry: entry[0],
    )

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "ROUND_HISTORY_ORDER\t" + "\t".join(label for _, label in ordered) + "\n",
        encoding="utf-8",
    )
    print(output.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
