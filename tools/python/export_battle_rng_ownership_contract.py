#!/usr/bin/env python3
"""Freeze the Python oracle's battle-scoped RNG ownership contract."""

from __future__ import annotations

import argparse
import ast
from pathlib import Path


def is_self_rng(node: ast.AST) -> bool:
    return (
        isinstance(node, ast.Attribute)
        and node.attr == "rng"
        and isinstance(node.value, ast.Name)
        and node.value.id == "self"
    )


def references_self_rng(node: ast.AST) -> bool:
    return any(is_self_rng(child) for child in ast.walk(node))


def calls_named(node: ast.AST, method_name: str) -> bool:
    for child in ast.walk(node):
        if not isinstance(child, ast.Call):
            continue
        func = child.func
        if isinstance(func, ast.Attribute) and func.attr == method_name:
            return True
        if isinstance(func, ast.Name) and func.id == method_name:
            return True
    return False


def find_class(root: ast.AST, name: str) -> ast.ClassDef:
    for node in ast.walk(root):
        if isinstance(node, ast.ClassDef) and node.name == name:
            return node
    raise RuntimeError(f"class {name!r} not found")


def find_method(class_node: ast.ClassDef, name: str) -> ast.FunctionDef | ast.AsyncFunctionDef:
    for node in class_node.body:
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == name:
            return node
    raise RuntimeError(f"method {class_node.name}.{name} not found")


def find_function(root: ast.AST, name: str) -> ast.FunctionDef | ast.AsyncFunctionDef:
    for node in ast.walk(root):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == name:
            return node
    raise RuntimeError(f"function {name!r} not found")


def rng_functions(root: ast.AST) -> list[str]:
    return sorted(
        {
            node.name
            for node in ast.walk(root)
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
            and references_self_rng(node)
        }
    )


def assignment_functions(root: ast.AST) -> list[str]:
    result: set[str] = set()
    for function in ast.walk(root):
        if not isinstance(function, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        for child in ast.walk(function):
            if isinstance(child, ast.Assign) and any(is_self_rng(target) for target in child.targets):
                result.add(function.name)
            elif isinstance(child, ast.AnnAssign) and is_self_rng(child.target):
                result.add(function.name)
    return sorted(result)


def phase_calls_delayed_with_battle(phase_source: str) -> bool:
    module = ast.parse(phase_source)
    controller = find_class(module, "PhaseController")
    start_round = find_method(controller, "start_round")
    for node in ast.walk(start_round):
        if not isinstance(node, ast.Call):
            continue
        func = node.func
        name = func.id if isinstance(func, ast.Name) else func.attr if isinstance(func, ast.Attribute) else ""
        if name != "resolve_delayed_hits" or not node.args:
            continue
        first = node.args[0]
        if isinstance(first, ast.Attribute) and first.attr == "battle":
            return True
    return False


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    root = Path(args.source_root)
    battle_path = root / "auto_ptu" / "rules" / "battle_state.py"
    phase_path = root / "auto_ptu" / "rules" / "controllers" / "phase_controller.py"

    battle_module = ast.parse(battle_path.read_text(encoding="utf-8"))
    resolve_move_action = find_function(battle_module, "resolve_move_action")
    resolve_move_targets = find_function(battle_module, "resolve_move_targets")

    rng_users = rng_functions(battle_module)
    assignments = assignment_functions(battle_module)

    values = [
        "BATTLE_RNG_OWNERSHIP",
        "1" if rng_users else "0",
        ",".join(assignments),
        ",".join(rng_users),
        "1" if references_self_rng(resolve_move_action) else "0",
        "1" if calls_named(resolve_move_targets, "resolve_move_action") else "0",
        "1" if phase_calls_delayed_with_battle(phase_path.read_text(encoding="utf-8")) else "0",
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\t".join(values) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
