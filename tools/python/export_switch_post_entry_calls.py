#!/usr/bin/env python3
"""Freeze the top-level helper-call sequence after Impostor in pinned Python _apply_switch."""
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

    calls: list[tuple[int, str]] = []
    for node in ast.walk(function):
        if not isinstance(node, ast.Call):
            continue
        name = call_name(node)
        if name:
            calls.append((node.lineno, name))
    calls.sort()

    impostor_lines = [line for line, name in calls if name.endswith("_trigger_impostor")]
    if not impostor_lines:
        raise RuntimeError("pinned _apply_switch no longer calls _trigger_impostor")
    boundary = min(impostor_lines)
    post = [(line, name) for line, name in calls if line > boundary]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        handle.write(f"IMPOSTOR_LINE\t{boundary}\n")
        for line, name in post:
            handle.write(f"CALL\t{line}\t{name}\n")
    print(output)


if __name__ == "__main__":
    main()
