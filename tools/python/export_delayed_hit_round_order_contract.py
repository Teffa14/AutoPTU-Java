#!/usr/bin/env python3
"""Freeze the placement of delayed-hit resolution inside Python ROUND_START."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path

ORDERED_CALLS = (
    "_advance_terrain",
    "_advance_zone_effects",
    "_advance_room_effects",
    "_resolve_delayed_hits",
    "_clear_expired_follow_me",
    "_clear_expired_foresight",
)


def find_start_round(tree: ast.Module) -> ast.FunctionDef:
    for node in tree.body:
        if isinstance(node, ast.ClassDef) and node.name == "PhaseController":
            for statement in node.body:
                if isinstance(statement, ast.FunctionDef) and statement.name == "start_round":
                    return statement
    raise RuntimeError("PhaseController.start_round not found")


def call_name(node: ast.Call) -> str:
    if isinstance(node.func, ast.Attribute):
        return node.func.attr
    if isinstance(node.func, ast.Name):
        return node.func.id
    return ""


def first_call_lines(method: ast.FunctionDef) -> dict[str, int]:
    found: dict[str, int] = {}
    for node in ast.walk(method):
        if not isinstance(node, ast.Call):
            continue
        name = call_name(node)
        if name in ORDERED_CALLS:
            found[name] = min(found.get(name, node.lineno), node.lineno)
    return found


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    path = args.source_root.resolve() / "auto_ptu" / "rules" / "controllers" / "phase_controller.py"
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    lines = first_call_lines(find_start_round(tree))
    missing = [name for name in ORDERED_CALLS if name not in lines]
    if missing:
        raise AssertionError(f"ROUND_START contract missing calls: {missing}")

    positions = [lines[name] for name in ORDERED_CALLS]
    if positions != sorted(positions) or len(set(positions)) != len(positions):
        raise AssertionError(f"ROUND_START delayed-hit order changed: {dict(zip(ORDERED_CALLS, positions))}")

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "DELAYED_HIT_ROUND_ORDER\t" + "\t".join(ORDERED_CALLS) + "\n",
        encoding="utf-8",
    )
    print(output.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
