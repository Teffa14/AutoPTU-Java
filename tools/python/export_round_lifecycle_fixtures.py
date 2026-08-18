#!/usr/bin/env python3
"""Extract the round-start lifecycle contract from Python PhaseController."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def find_start_round(tree: ast.Module) -> ast.FunctionDef:
    for node in tree.body:
        if isinstance(node, ast.ClassDef) and node.name == "PhaseController":
            for statement in node.body:
                if isinstance(statement, ast.FunctionDef) and statement.name == "start_round":
                    return statement
    raise RuntimeError("PhaseController.start_round not found")


def has_round_increment(method: ast.FunctionDef) -> bool:
    return any(
        isinstance(node, ast.AugAssign)
        and isinstance(node.target, ast.Attribute)
        and node.target.attr == "round"
        and isinstance(node.op, ast.Add)
        and isinstance(node.value, ast.Constant)
        and node.value.value == 1
        for node in ast.walk(method)
    )


def reset_targets(method: ast.FunctionDef) -> set[str]:
    targets: set[str] = set()
    for node in ast.walk(method):
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr == "reset_actions":
            if isinstance(node.func.value, ast.Name):
                targets.add(node.func.value.id)
    return targets


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    path = args.source_root.resolve() / "auto_ptu" / "rules" / "controllers" / "phase_controller.py"
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    method = find_start_round(tree)
    resetters = reset_targets(method)

    fixtures = [
        ("round_increment", "1" if has_round_increment(method) else "0"),
        ("trainer_actions_reset_at_round_start", "1" if "trainer" in resetters else "0"),
        ("pokemon_actions_reset_at_round_start", "1" if "mon" in resetters or "pokemon" in resetters else "0"),
    ]

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{name}\t{value}" for name, value in fixtures) + "\n", encoding="utf-8")
    print(f"wrote {len(fixtures)} Python round lifecycle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
