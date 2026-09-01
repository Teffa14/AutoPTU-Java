#!/usr/bin/env python3
"""Export deterministic Shadow Tag candidate-step outcomes from the pinned Python oracle."""
from __future__ import annotations

import argparse
import ast
import sys
from pathlib import Path

BATTLE_STATE = Path("auto_ptu/rules/battle_state.py")

CASES = (
    ("medium_boundary", "Medium", (4, 4), (0, 4), (1, 0), 4),
    ("huge_footprint", "Huge", (5, 4), (0, 4), (1, 0), 3),
    ("gigantic_footprint", "Gigantic", (5, 4), (0, 4), (1, 0), 3),
    ("diagonal_boundary", "Medium", (4, 4), (0, 0), (1, 1), 4),
    ("toward_anchor_inside", "Medium", (5, 4), (0, 4), (-1, 0), 3),
    ("already_outside_inward", "Medium", (7, 4), (0, 4), (-1, 0), 3),
)


def call_name(call: ast.Call) -> str:
    if isinstance(call.func, ast.Attribute):
        return call.func.attr
    if isinstance(call.func, ast.Name):
        return call.func.id
    return ""


def contains_distance_call(node: ast.AST) -> bool:
    return any(
        isinstance(child, ast.Call) and call_name(child) == "_combatant_distance_to_coord"
        for child in ast.walk(node)
    )


def shadow_limit(source: Path) -> int:
    tree = ast.parse(source.read_text(encoding="utf-8-sig"), filename=str(source))
    functions = [
        node for node in ast.walk(tree)
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == "apply_forced_movement"
    ]
    if len(functions) != 1:
        raise SystemExit(f"expected one apply_forced_movement, found {len(functions)}")
    guards = []
    for node in ast.walk(functions[0]):
        if isinstance(node, ast.If) and contains_distance_call(node.test):
            guards.append(node.test)
    if len(guards) != 1:
        raise SystemExit(f"expected one Shadow Tag distance guard, found {len(guards)}")

    comparisons = [
        child for child in ast.walk(guards[0])
        if isinstance(child, ast.Compare) and contains_distance_call(child)
    ]
    if len(comparisons) != 1:
        raise SystemExit(f"expected one Shadow Tag distance comparison, found {len(comparisons)}")
    compare = comparisons[0]
    if len(compare.ops) != 1 or not isinstance(compare.ops[0], ast.Gt):
        raise SystemExit("Shadow Tag distance comparison is no longer >")
    if len(compare.comparators) != 1 or not isinstance(compare.comparators[0], ast.Constant):
        raise SystemExit("Shadow Tag guard limit is no longer a literal")
    value = compare.comparators[0].value
    if not isinstance(value, int):
        raise SystemExit("Shadow Tag guard limit is no longer an integer")
    return value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    limit = shadow_limit(args.source_root / BATTLE_STATE)
    sys.path.insert(0, str(args.source_root))
    from auto_ptu.rules import targeting  # type: ignore

    lines = [
        "case_id\tsize\tstart_x\tstart_y\tanchor_x\tanchor_y\tdx\tdy\trequested\tlimit\tdestination_x\tdestination_y\tmoved"
    ]
    for case_id, size, start, anchor, direction, requested in CASES:
        current = start
        moved = 0
        for _ in range(requested):
            candidate = (current[0] + direction[0], current[1] + direction[1])
            if targeting.footprint_distance(candidate, size, anchor, "Medium", None) > limit:
                break
            current = candidate
            moved += 1
        lines.append("\t".join(map(str, (
            case_id,
            size,
            start[0],
            start[1],
            anchor[0],
            anchor[1],
            direction[0],
            direction[1],
            requested,
            limit,
            current[0],
            current[1],
            moved,
        ))))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
