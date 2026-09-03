#!/usr/bin/env python3
"""Freeze the Python oracle ordering between forced movement and tile-entry traps."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path

SOURCE = Path("auto_ptu/rules/battle_state.py")
TRAP_HOOK = "_trigger_tile_traps_on_entry"
FORCED_EFFECT = "forced_movement"


def call_name(node: ast.Call) -> str:
    func = node.func
    if isinstance(func, ast.Attribute):
        return func.attr
    if isinstance(func, ast.Name):
        return func.id
    return ""


def contains_string(node: ast.AST, value: str) -> bool:
    return any(isinstance(child, ast.Constant) and child.value == value for child in ast.walk(node))


def containing_function(tree: ast.AST, target: ast.AST) -> ast.FunctionDef | ast.AsyncFunctionDef | None:
    candidates: list[ast.FunctionDef | ast.AsyncFunctionDef] = []
    target_line = getattr(target, "lineno", -1)
    for node in ast.walk(tree):
        if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        start = getattr(node, "lineno", -1)
        end = getattr(node, "end_lineno", start)
        if start <= target_line <= end:
            candidates.append(node)
    if not candidates:
        return None
    return max(candidates, key=lambda node: node.lineno)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_root / SOURCE
    tree = ast.parse(source.read_text(encoding="utf-8-sig"), filename=str(source))
    trap_calls = [
        node for node in ast.walk(tree)
        if isinstance(node, ast.Call) and call_name(node) == TRAP_HOOK
    ]
    if not trap_calls:
        raise SystemExit(f"no {TRAP_HOOK} calls found in pinned oracle")

    rows: list[tuple[str, int, int]] = []
    for trap_call in sorted(trap_calls, key=lambda node: node.lineno):
        owner = containing_function(tree, trap_call)
        if owner is None:
            raise SystemExit(f"trap hook at line {trap_call.lineno} has no containing function")
        preceding_forced_events = [
            node for node in ast.walk(owner)
            if isinstance(node, ast.Call)
            and node.lineno < trap_call.lineno
            and call_name(node) == "log_event"
            and contains_string(node, FORCED_EFFECT)
        ]
        if not preceding_forced_events:
            raise SystemExit(
                f"{owner.name}: {TRAP_HOOK} at line {trap_call.lineno} is not preceded by a "
                f"log_event containing {FORCED_EFFECT!r}"
            )
        forced_event = max(preceding_forced_events, key=lambda node: node.lineno)
        rows.append((owner.name, forced_event.lineno, trap_call.lineno))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    text = "function\tforced_movement_event_line\ttrap_entry_line\n"
    text += "\n".join(f"{name}\t{event_line}\t{trap_line}" for name, event_line, trap_line in rows) + "\n"
    args.output.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
