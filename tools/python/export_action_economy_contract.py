#!/usr/bin/env python3
"""Freeze the pinned Python action-consumption boundary as a language-neutral contract."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def find_method(tree: ast.Module, class_name: str, method_name: str) -> ast.FunctionDef:
    for node in tree.body:
        if isinstance(node, ast.ClassDef) and node.name == class_name:
            for statement in node.body:
                if isinstance(statement, ast.FunctionDef) and statement.name == method_name:
                    return statement
    raise RuntimeError(f"method not found: {class_name}.{method_name}")


def attr_chain(node: ast.AST) -> str | None:
    parts: list[str] = []
    current = node
    while isinstance(current, ast.Attribute):
        parts.append(current.attr)
        current = current.value
    if isinstance(current, ast.Name):
        parts.append(current.id)
        return ".".join(reversed(parts))
    return None


def call_shapes(method: ast.FunctionDef) -> list[tuple[str, list[str]]]:
    result: list[tuple[str, list[str]]] = []
    for node in ast.walk(method):
        if not isinstance(node, ast.Call):
            continue
        name = attr_chain(node.func)
        if name is None:
            continue
        args: list[str] = []
        for arg in node.args:
            chain = attr_chain(arg)
            if chain is not None:
                args.append(chain)
            elif isinstance(arg, ast.Constant):
                args.append(repr(arg.value))
            else:
                args.append(ast.dump(arg, include_attributes=False))
        result.append((name, args))
    return result


def has_call(calls: list[tuple[str, list[str]]], name: str, first_arg: str) -> bool:
    return any(call_name == name and args and args[0] == first_arg for call_name, args in calls)


def action_type_comparisons(method: ast.FunctionDef) -> set[str]:
    compared: set[str] = set()
    for node in ast.walk(method):
        if not isinstance(node, ast.Compare):
            continue
        candidates = [node.left, *node.comparators]
        has_requested_type = any(attr_chain(candidate) == "action.action_type" for candidate in candidates)
        if not has_requested_type:
            continue
        for candidate in candidates:
            if isinstance(candidate, ast.Attribute) and isinstance(candidate.value, ast.Name) and candidate.value.id == "ActionType":
                compared.add(candidate.attr)
    return compared


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_root.resolve() / "auto_ptu" / "rules" / "controllers" / "action_resolver.py"
    tree = ast.parse(source.read_text(encoding="utf-8"), filename=str(source))
    method = find_method(tree, "ActionResolver", "resolve_next_action")
    calls = call_shapes(method)

    required = {
        "trainer_check_requested_type": has_call(calls, "trainer.has_action_available", "action.action_type"),
        "trainer_mark_requested_type": has_call(calls, "trainer.mark_action", "action.action_type"),
        "pokemon_check_requested_type": has_call(calls, "mon_state.has_action_available", "action.action_type"),
        "pokemon_extra_fallback_requested_type": has_call(calls, "battle._consume_extra_action", "mon_state")
            and any(name == "battle._consume_extra_action" and len(call_args) >= 2 and call_args[1] == "action.action_type" for name, call_args in calls),
        "pokemon_mark_requested_type": has_call(calls, "mon_state.mark_action", "action.action_type"),
    }
    missing = [name for name, present in required.items() if not present]
    if missing:
        raise AssertionError("missing Python action-consumption contract: " + ", ".join(missing))

    compared_types = action_type_comparisons(method)
    full_special_case = "FULL" in compared_types
    standard_conversion_case = "STANDARD" in compared_types

    rows = [
        ("CONSUMPTION", "trainer", "check", "requested_action_type"),
        ("CONSUMPTION", "trainer", "mark", "requested_action_type"),
        ("CONSUMPTION", "pokemon", "check", "requested_action_type"),
        ("CONSUMPTION", "pokemon", "extra_fallback", "requested_action_type"),
        ("CONSUMPTION", "pokemon", "mark", "requested_action_type"),
        ("RESOLVER_SPECIAL_CASE", "full_composition", str(full_special_case).lower(), "resolve_next_action"),
        ("RESOLVER_SPECIAL_CASE", "standard_conversion", str(standard_conversion_case).lower(), "resolve_next_action"),
    ]

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join("\t".join(row) for row in rows) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} Python action-economy contract rows to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
