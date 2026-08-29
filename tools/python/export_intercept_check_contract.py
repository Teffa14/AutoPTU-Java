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


def called_function_names(function: ast.FunctionDef) -> list[str]:
    names: set[str] = set()
    for node in ast.walk(function):
        if not isinstance(node, ast.Call):
            continue
        if isinstance(node.func, ast.Name):
            names.add(node.func.id)
        elif isinstance(node.func, ast.Attribute):
            names.add(node.func.attr)
    return sorted(names)


def string_literals(function: ast.FunctionDef) -> list[str]:
    return sorted({
        str(node.value)
        for node in ast.walk(function)
        if isinstance(node, ast.Constant) and isinstance(node.value, str)
    })


def integer_literals(function: ast.FunctionDef) -> list[int]:
    return sorted({
        int(node.value)
        for node in ast.walk(function)
        if isinstance(node, ast.Constant)
        and isinstance(node.value, int)
        and not isinstance(node.value, bool)
    })


def write_terrain_contract(tree: ast.AST, output: Path) -> None:
    function = find_function(tree, "_terrain_skill_check_bonus")
    rows = {
        "terrain_skill_check_bonus_source": normalized(function),
        "terrain_skill_check_bonus_calls": "|".join(called_function_names(function)),
        "terrain_skill_check_bonus_strings": "|".join(string_literals(function)),
        "terrain_skill_check_bonus_integers": "|".join(str(value) for value in integer_literals(function)),
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(f"{key}\t{value}" for key, value in rows.items()) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--terrain-output")
    args = parser.parse_args()

    path = Path(args.source_root) / "auto_ptu" / "rules" / "battle_state.py"
    source = path.read_text(encoding="utf-8")
    tree = ast.parse(source)
    function = find_function(tree, "_attempt_intercept")
    src = normalized(function)

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
    }
    values = {
        "justified_errata_bonus": intercept_justified_bonus(function),
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

    if args.terrain_output:
        write_terrain_contract(tree, Path(args.terrain_output))


if __name__ == "__main__":
    main()
