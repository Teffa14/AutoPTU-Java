#!/usr/bin/env python3
"""Export the pinned Python delayed-hit execution boundary.

This deliberately freezes the language-neutral entrypoint contract before Java
wires delayed hits into ROUND_START. Delayed hits are scheduled separately and,
when due, enter BattleState.resolve_move_targets directly rather than the normal
resolve_move_action/action-selection path.
"""

from __future__ import annotations

import argparse
import ast
import inspect
import sys
import textwrap
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args()


def function_tree(fn) -> ast.AST:
    return ast.parse(textwrap.dedent(inspect.getsource(fn)))


def call_name(node: ast.Call) -> str:
    target = node.func
    if isinstance(target, ast.Name):
        return target.id
    if isinstance(target, ast.Attribute):
        return target.attr
    return ""


def call_names(fn) -> list[str]:
    return [
        name
        for node in ast.walk(function_tree(fn))
        if isinstance(node, ast.Call)
        if (name := call_name(node))
    ]


def keyword_names_for_call(fn, expected_call: str) -> set[str]:
    names: set[str] = set()
    for node in ast.walk(function_tree(fn)):
        if not isinstance(node, ast.Call) or call_name(node) != expected_call:
            continue
        names.update(keyword.arg for keyword in node.keywords if keyword.arg)
    return names


def main() -> int:
    args = parse_args()
    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.rules.battle_state import BattleState
    from auto_ptu.rules.hooks.move_effect_tools import resolve_delayed_hits

    delayed_calls = call_names(resolve_delayed_hits)
    target_calls = call_names(BattleState.resolve_move_targets)
    forwarded = keyword_names_for_call(resolve_delayed_hits, "resolve_move_targets")

    # These checks intentionally fail the exporter if Python changes the
    # execution boundary. Java must then be reviewed rather than silently
    # preserving an obsolete assumption.
    assert "resolve_move_targets" in delayed_calls
    assert "resolve_move_action" not in delayed_calls
    assert "target_id" in forwarded
    assert "target_position" in forwarded

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\t".join(
            [
                "TARGET_RESOLUTION",
                "1" if "resolve_move_targets" in delayed_calls else "0",
                "1" if "resolve_move_action" in delayed_calls else "0",
                "1" if "target_id" in forwarded else "0",
                "1" if "target_position" in forwarded else "0",
                "1" if "resolve_move_action" in target_calls else "0",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
