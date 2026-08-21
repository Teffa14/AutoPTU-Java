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


def method_map(class_node: ast.ClassDef) -> dict[str, ast.FunctionDef | ast.AsyncFunctionDef]:
    return {
        node.name: node
        for node in class_node.body
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
    }


def references_self_rng(node: ast.AST) -> bool:
    return any(is_self_rng(child) for child in ast.walk(node))


def calls_method(node: ast.AST, method_name: str) -> bool:
    for child in ast.walk(node):
        if not isinstance(child, ast.Call):
            continue
        func = child.func
        if isinstance(func, ast.Attribute) and func.attr == method_name:
            return True
    return False


def assignment_methods(class_node: ast.ClassDef) -> list[str]:
    result: list[str] = []
    for method in method_map(class_node).values():
        for child in ast.walk(method):
            if isinstance(child, ast.Assign):
                if any(is_self_rng(target) for target in child.targets):
                    result.append(method.name)
            elif isinstance(child, ast.AnnAssign) and is_self_rng(child.target):
                result.append(method.name)
    return sorted(set(result))


def find_class(module: ast.Module, name: str) -> ast.ClassDef:
    for node in module.body:
        if isinstance(node, ast.ClassDef) and node.name == name:
            return node
    raise RuntimeError(f"class {name!r} not found")


def find_method(class_node: ast.ClassDef, name: str) -> ast.FunctionDef | ast.AsyncFunctionDef:
    methods = method_map(class_node)
    if name not in methods:
        raise RuntimeError(f"method {class_node.name}.{name} not found")
    return methods[name]


def phase_calls_delayed_with_battle(phase_source: str) -> bool:
    module = ast.parse(phase_source)
    controller = find_class(module, "PhaseController")
    start_round = find_method(controller, "start_round")
    for node in ast.walk(start_round):
        if not isinstance(node, ast.Call):
            continue
        func = node.func
        name = func.id if isinstance(func, ast.Name) else func.attr if isinstance(func, ast.Attribute) else ""
        if name != "resolve_delayed_hits":
            continue
        if node.args:
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

    battle_source = battle_path.read_text(encoding="utf-8")
    battle_module = ast.parse(battle_source)
    battle = find_class(battle_module, "BattleState")
    methods = method_map(battle)

    rng_methods = sorted(name for name, method in methods.items() if references_self_rng(method))
    assignments = assignment_methods(battle)

    resolve_move_action = methods.get("resolve_move_action")
    resolve_move_targets = methods.get("resolve_move_targets")

    values = [
        "BATTLE_RNG_OWNERSHIP",
        "1" if rng_methods else "0",
        ",".join(assignments),
        ",".join(rng_methods),
        "1" if resolve_move_action is not None and references_self_rng(resolve_move_action) else "0",
        "1" if resolve_move_targets is not None and calls_method(resolve_move_targets, "resolve_move_action") else "0",
        "1" if phase_calls_delayed_with_battle(phase_path.read_text(encoding="utf-8")) else "0",
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\t".join(values) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
