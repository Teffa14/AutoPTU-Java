#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def find_function(tree: ast.AST, name: str) -> ast.FunctionDef:
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef) and node.name == name:
            return node
    raise RuntimeError(f"missing Python function: {name}")


def normalized(node: ast.AST) -> str:
    return " ".join(ast.unparse(node).lower().split())


def intercept_justified_bonus(function: ast.FunctionDef) -> int:
    for node in ast.walk(function):
        if not isinstance(node, ast.Assign) or len(node.targets) != 1:
            continue
        target = node.targets[0]
        if not isinstance(target, ast.Name) or target.id != "intercept_bonus":
            continue
        value = node.value
        if not isinstance(value, ast.IfExp):
            continue
        condition = normalized(value.test)
        if "has_ability_exact" not in condition or "justified [errata]" not in condition:
            continue
        if not isinstance(value.body, ast.Constant) or not isinstance(value.body.value, int):
            continue
        if not isinstance(value.orelse, ast.Constant) or value.orelse.value != 0:
            continue
        return int(value.body.value)
    raise RuntimeError("missing exact Justified [Errata] intercept bonus assignment")


def terrain_skill_contract(function: ast.FunctionDef) -> tuple[set[str], int, bool, bool]:
    eligible: set[str] = set()
    bonus = None
    src = normalized(function)

    for node in ast.walk(function):
        if isinstance(node, ast.Compare) and len(node.ops) == 1 and isinstance(node.ops[0], ast.NotIn):
            if len(node.comparators) != 1 or not isinstance(node.comparators[0], ast.Set):
                continue
            left = normalized(node.left)
            if "normalized_skill" not in left:
                continue
            values = node.comparators[0].elts
            if all(isinstance(value, ast.Constant) and isinstance(value.value, str) for value in values):
                eligible = {str(value.value).strip().lower() for value in values}
        if isinstance(node, ast.Return) and isinstance(node.value, ast.IfExp):
            value = node.value
            if "_matches_naturewalk_terrain" not in normalized(value.test):
                continue
            if isinstance(value.body, ast.Constant) and isinstance(value.body.value, int):
                if isinstance(value.orelse, ast.Constant) and value.orelse.value == 0:
                    bonus = int(value.body.value)

    if not eligible:
        raise RuntimeError("missing terrain skill eligibility set")
    if bonus is None:
        raise RuntimeError("missing Naturewalk terrain skill bonus")

    requires_survivalist = "has_trainer_feature('survivalist')" in src or 'has_trainer_feature("survivalist")' in src
    uses_naturewalk_match = "_matches_naturewalk_terrain" in src
    return eligible, bonus, requires_survivalist, uses_naturewalk_match


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    path = Path(args.source_root) / "auto_ptu" / "rules" / "battle_state.py"
    source = path.read_text(encoding="utf-8")
    tree = ast.parse(source)
    function = find_function(tree, "_attempt_intercept")
    src = normalized(function)
    terrain_function = find_function(tree, "_terrain_skill_check_bonus")
    terrain_skills, terrain_bonus, requires_survivalist, uses_naturewalk_match = terrain_skill_contract(terrain_function)

    # Freeze only the reusable check arithmetic here. Geometry, candidate selection and
    # movement remain separate contracts so Java can compose them without one monolith.
    flags = {
        "uses_d20": "randint(1, 20)" in src,
        "uses_best_acrobatics_athletics": "max(" in src and "acrobatics" in src and "athletics" in src,
        "uses_justified_errata": "justified [errata]" in src,
        "uses_terrain_intercept_bonus": "terrain" in src and "intercept" in src and "bonus" in src,
        "dc_is_distance_times_three": "distance * 3" in src or "3 * distance" in src,
        "coaching_can_force_success": "coaching" in src and "success" in src,
        "success_uses_greater_equal": ">= dc" in src or ">= check_dc" in src,
        "terrain_requires_survivalist": requires_survivalist,
        "terrain_uses_naturewalk_match": uses_naturewalk_match,
        "terrain_skill_athletics": "athletics" in terrain_skills,
        "terrain_skill_acrobatics": "acrobatics" in terrain_skills,
        "terrain_skill_stealth": "stealth" in terrain_skills,
        "terrain_skill_perception": "perception" in terrain_skills,
        "terrain_skill_survival": "survival" in terrain_skills,
    }
    values = {
        "justified_errata_bonus": intercept_justified_bonus(function),
        "terrain_skill_bonus": terrain_bonus,
        "terrain_skill_count": len(terrain_skills),
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(
            [*(f"{key}\t{1 if value else 0}" for key, value in flags.items()),
             *(f"{key}\t{value}" for key, value in values.items())]
        ) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
