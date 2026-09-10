#!/usr/bin/env python3
"""Freeze semantic stage order after Impostor in pinned Python _apply_switch."""
from __future__ import annotations

import argparse
import ast
import inspect
import sys
import textwrap
from pathlib import Path


def call_name(node: ast.Call) -> str | None:
    func = node.func
    if isinstance(func, ast.Attribute):
        parts: list[str] = [func.attr]
        value = func.value
        while isinstance(value, ast.Attribute):
            parts.append(value.attr)
            value = value.value
        if isinstance(value, ast.Name):
            parts.append(value.id)
        return ".".join(reversed(parts))
    if isinstance(func, ast.Name):
        return func.id
    return None


def constant_string(node: ast.AST) -> str | None:
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    return None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.rules import BattleState

    source = textwrap.dedent(inspect.getsource(BattleState._apply_switch))
    tree = ast.parse(source)
    function = tree.body[0]
    if not isinstance(function, (ast.FunctionDef, ast.AsyncFunctionDef)):
        raise RuntimeError("_apply_switch source did not parse as a function")

    calls: list[tuple[int, str, ast.Call]] = []
    for node in ast.walk(function):
        if not isinstance(node, ast.Call):
            continue
        name = call_name(node)
        if name:
            calls.append((node.lineno, name, node))
    calls.sort(key=lambda entry: entry[0])

    impostor_lines = [line for line, name, _node in calls if name.endswith("_trigger_impostor")]
    if not impostor_lines:
        raise RuntimeError("pinned _apply_switch no longer calls _trigger_impostor")
    boundary = min(impostor_lines)
    post = [(line, name, node) for line, name, node in calls if line > boundary]

    semantic: list[tuple[int, str]] = []
    for line, name, node in post:
        if name.endswith("_trigger_ball_fetch"):
            semantic.append((line, "BALL_FETCH"))
        elif name.endswith("has_ability") and node.args and constant_string(node.args[0]) == "Curious Medicine":
            semantic.append((line, "CURIOUS_MEDICINE"))
        elif name.endswith("_insert_replacement_initiative"):
            semantic.append((line, "INSERT_REPLACEMENT_INITIATIVE"))
        elif name.endswith("_maybe_trigger_first_blood"):
            semantic.append((line, "FIRST_BLOOD"))
        elif name.endswith("_maybe_trigger_quick_switch"):
            semantic.append((line, "QUICK_SWITCH"))

    semantic.sort()
    stages = [stage for _line, stage in semantic]
    expected = [
        "BALL_FETCH",
        "CURIOUS_MEDICINE",
        "INSERT_REPLACEMENT_INITIATIVE",
        "FIRST_BLOOD",
        "QUICK_SWITCH",
    ]
    if stages != expected:
        raise RuntimeError(f"pinned post-entry family order changed: {stages!r}")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        handle.write(f"IMPOSTOR_LINE\t{boundary}\n")
        handle.write("ORDER\t" + ",".join(stages) + "\n")
        for line, name, _node in post:
            handle.write(f"CALL\t{line}\t{name}\n")
    print(output)


if __name__ == "__main__":
    main()
