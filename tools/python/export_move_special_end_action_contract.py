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


def assignments(function: ast.FunctionDef | ast.AsyncFunctionDef, name: str) -> list[tuple[int, ast.expr]]:
    found: list[tuple[int, ast.expr]] = []
    for node in ast.walk(function):
        if not isinstance(node, ast.Assign):
            continue
        if any(isinstance(target, ast.Name) and target.id == name for target in node.targets):
            found.append((node.lineno, node.value))
    return sorted(found, key=lambda item: item[0])


def first_assignment_source(function: ast.FunctionDef | ast.AsyncFunctionDef, name: str) -> str:
    found = assignments(function, name)
    if not found:
        return ""
    return ast.unparse(found[0][1])


def contains_name(node: ast.AST, names: set[str]) -> bool:
    return any(isinstance(child, ast.Name) and child.id in names for child in ast.walk(node))


def damage_updates(function: ast.FunctionDef | ast.AsyncFunctionDef, end_line: int) -> list[tuple[int, ast.AST]]:
    found: list[tuple[int, ast.AST]] = []
    for node in ast.walk(function):
        if node.lineno >= end_line:
            continue
        if isinstance(node, ast.AugAssign) and isinstance(node.target, ast.Name):
            if node.target.id == "total_damage_dealt" and isinstance(node.op, ast.Add):
                found.append((node.lineno, node.value))
        elif isinstance(node, ast.Assign):
            if any(isinstance(target, ast.Name) and target.id == "total_damage_dealt" for target in node.targets):
                if isinstance(node.value, ast.BinOp) and isinstance(node.value.op, ast.Add):
                    found.append((node.lineno, node.value))
    return sorted(found, key=lambda item: item[0])


def smallest_loop_containing(function: ast.FunctionDef | ast.AsyncFunctionDef, line: int) -> ast.AST | None:
    loops: list[ast.AST] = []
    for node in ast.walk(function):
        if not isinstance(node, (ast.For, ast.AsyncFor)):
            continue
        end_line = getattr(node, "end_lineno", node.lineno)
        if node.lineno <= line <= end_line:
            loops.append(node)
    if not loops:
        return None
    return min(loops, key=lambda node: getattr(node, "end_lineno", node.lineno) - node.lineno)


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

    last_result_assignments = [
        item for item in assignments(function, "last_result")
        if item[0] < call.lineno and ast.unparse(item[1]) != initial_last_result
    ]
    if not last_result_assignments:
        raise AssertionError("END_ACTION must replace last_result while resolving targets")
    last_result_line, last_result_value = last_result_assignments[-1]
    last_result_update = ast.unparse(last_result_value)

    total_updates = damage_updates(function, call.lineno)
    if not total_updates:
        raise AssertionError("END_ACTION must accumulate total_damage_dealt before dispatch")
    total_damage_line, total_damage_value = total_updates[-1]
    total_damage_update = ast.unparse(total_damage_value)

    last_loop = smallest_loop_containing(function, last_result_line)
    damage_loop = smallest_loop_containing(function, total_damage_line)
    same_target_loop = last_loop is not None and last_loop is damage_loop
    last_result_uses_target_result = contains_name(last_result_value, {"result"})
    total_damage_uses_target_damage = contains_name(total_damage_value, {"damage_dealt", "damage", "result"})

    print(f"file={path.relative_to(root).as_posix()}")
    print(f"line={call.lineno}")
    print(f"defender={defender}")
    print(f"result={result}")
    print(f"damage_dealt={damage}")
    print(f"move={move}")
    print(f"initial_last_result={initial_last_result}")
    print(f"initial_total_damage_dealt={initial_total_damage}")
    print(f"last_result_update={last_result_update}")
    print(f"total_damage_update={total_damage_update}")
    print(f"last_result_uses_target_result={int(last_result_uses_target_result)}")
    print(f"total_damage_uses_target_damage={int(total_damage_uses_target_damage)}")
    print(f"aggregation_updates_share_target_loop={int(same_target_loop)}")

    if defender != "None":
        raise AssertionError(f"END_ACTION defender changed: {defender!r}")
    if result != "last_result":
        raise AssertionError(f"END_ACTION result changed: {result!r}")
    if damage != "total_damage_dealt":
        raise AssertionError(f"END_ACTION damage_dealt changed: {damage!r}")
    if not move:
        raise AssertionError("END_ACTION must forward the move")
    if initial_last_result not in {
        "{'hit': False, 'immutable_mind': True}",
        '{"hit": False, "immutable_mind": True}',
    }:
        raise AssertionError(f"END_ACTION last_result initial state changed: {initial_last_result!r}")
    if initial_total_damage != "0":
        raise AssertionError(f"END_ACTION total_damage_dealt initial state changed: {initial_total_damage!r}")
    if not last_result_uses_target_result:
        raise AssertionError(f"last_result no longer derives from each target result: {last_result_update!r}")
    if not total_damage_uses_target_damage:
        raise AssertionError(f"total_damage_dealt no longer derives from target damage: {total_damage_update!r}")
    if not same_target_loop:
        raise AssertionError("last_result replacement and total damage accumulation must share the target loop")
    if max(last_result_line, total_damage_line) >= call.lineno:
        raise AssertionError("target aggregation must finish before END_ACTION dispatch")


if __name__ == "__main__":
    main()
