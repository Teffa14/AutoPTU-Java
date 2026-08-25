#!/usr/bin/env python3
"""Freeze Python's action-wide move-special END_ACTION dispatch contract."""
from __future__ import annotations

import ast
import os
from pathlib import Path


def call_name(node: ast.Call) -> str:
    fn = node.func
    parts: list[str] = []
    while isinstance(fn, ast.Attribute):
        parts.append(fn.attr)
        fn = fn.value
    if isinstance(fn, ast.Name):
        parts.append(fn.id)
    return ".".join(reversed(parts))


def phase_of(call: ast.Call) -> str:
    for kw in call.keywords:
        if kw.arg == "phase" and isinstance(kw.value, ast.Constant):
            return str(kw.value.value or "").strip().lower()
    return "post_damage"


def keyword_source(call: ast.Call, name: str) -> str:
    for kw in call.keywords:
        if kw.arg == name:
            return ast.unparse(kw.value)
    return ""


def first_assignment_source(function: ast.FunctionDef | ast.AsyncFunctionDef, name: str) -> str:
    assignments: list[tuple[int, ast.expr]] = []
    for node in ast.walk(function):
        if not isinstance(node, ast.Assign):
            continue
        if any(isinstance(target, ast.Name) and target.id == name for target in node.targets):
            assignments.append((node.lineno, node.value))
    if not assignments:
        return ""
    _line, value = min(assignments, key=lambda item: item[0])
    return ast.unparse(value)


def main() -> None:
    root = Path(os.environ.get("AUTOPTU_PYTHON_ROOT", "../AutoPTU")).resolve()
    package = root / "auto_ptu"
    if not package.is_dir():
        raise SystemExit(f"AutoPTU package not found: {package}")

    end_calls: list[tuple[Path, ast.FunctionDef | ast.AsyncFunctionDef, ast.Call]] = []
    for path in sorted(package.rglob("*.py")):
        source = path.read_text(encoding="utf-8")
        try:
            tree = ast.parse(source, filename=str(path))
        except SyntaxError:
            continue
        for function in (node for node in ast.walk(tree) if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))):
            for node in ast.walk(function):
                if isinstance(node, ast.Call) and "handle_move_specials" in call_name(node) and phase_of(node) == "end_action":
                    end_calls.append((path, function, node))

    if len(end_calls) != 1:
        raise AssertionError(f"expected exactly one END_ACTION move-special dispatch, found {len(end_calls)}")

    path, function, call = end_calls[0]
    defender = keyword_source(call, "defender_id") or keyword_source(call, "defender")
    result = keyword_source(call, "result")
    damage = keyword_source(call, "damage_dealt")
    move = keyword_source(call, "move")
    initial_last_result = first_assignment_source(function, "last_result")
    initial_total_damage = first_assignment_source(function, "total_damage_dealt")

    print(f"file={path.relative_to(root).as_posix()}")
    print(f"line={call.lineno}")
    print(f"defender={defender}")
    print(f"result={result}")
    print(f"damage_dealt={damage}")
    print(f"move={move}")
    print(f"initial_last_result={initial_last_result}")
    print(f"initial_total_damage_dealt={initial_total_damage}")

    if defender != "None":
        raise AssertionError(f"END_ACTION defender changed: {defender!r}")
    if result != "last_result":
        raise AssertionError(f"END_ACTION result changed: {result!r}")
    if damage != "total_damage_dealt":
        raise AssertionError(f"END_ACTION damage_dealt changed: {damage!r}")
    if not move:
        raise AssertionError("END_ACTION must forward the move")
    if initial_last_result not in {"{'hit': False}", '{"hit": False}'}:
        raise AssertionError(f"END_ACTION last_result initial state changed: {initial_last_result!r}")
    if initial_total_damage != "0":
        raise AssertionError(f"END_ACTION total_damage_dealt initial state changed: {initial_total_damage!r}")


if __name__ == "__main__":
    main()
