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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_root / SOURCE
    tree = ast.parse(source.read_text(encoding="utf-8-sig"), filename=str(source))

    rows: list[tuple[str, int, int]] = []
    forced_event_functions = 0
    for owner in ast.walk(tree):
        if not isinstance(owner, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        calls = [node for node in ast.walk(owner) if isinstance(node, ast.Call)]
        forced_events = [
            node for node in calls
            if call_name(node) == "log_event" and contains_string(node, FORCED_EFFECT)
        ]
        if not forced_events:
            continue
        forced_event_functions += 1
        trap_calls = [node for node in calls if call_name(node) == TRAP_HOOK]
        if not trap_calls:
            raise SystemExit(
                f"{owner.name}: emits {FORCED_EFFECT!r} but has no {TRAP_HOOK} landing hook"
            )
        for forced_event in sorted(forced_events, key=lambda node: node.lineno):
            following_traps = [node for node in trap_calls if node.lineno > forced_event.lineno]
            if not following_traps:
                raise SystemExit(
                    f"{owner.name}: {FORCED_EFFECT!r} event at line {forced_event.lineno} is not followed by "
                    f"{TRAP_HOOK}"
                )
            trap_call = min(following_traps, key=lambda node: node.lineno)
            rows.append((owner.name, forced_event.lineno, trap_call.lineno))

    if forced_event_functions == 0:
        raise SystemExit(f"no functions emitting {FORCED_EFFECT!r} found in pinned oracle")
    if not rows:
        raise SystemExit(f"no {FORCED_EFFECT!r} -> {TRAP_HOOK} ordering rows produced")

    rows.sort(key=lambda row: (row[1], row[2], row[0]))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    text = "function\tforced_movement_event_line\ttrap_entry_line\n"
    text += "\n".join(f"{name}\t{event_line}\t{trap_line}" for name, event_line, trap_line in rows) + "\n"
    args.output.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
