#!/usr/bin/env python3
"""Freeze how delayed and ordinary move resolution share the battle RNG."""

from __future__ import annotations

import argparse
import ast
import inspect
import sys
import textwrap
from pathlib import Path


def call_name(node: ast.Call) -> str:
    target = node.func
    if isinstance(target, ast.Name):
        return target.id
    if isinstance(target, ast.Attribute):
        return target.attr
    return ""


def is_self_rng(node: ast.AST) -> bool:
    return (
        isinstance(node, ast.Attribute)
        and node.attr == "rng"
        and isinstance(node.value, ast.Name)
        and node.value.id == "self"
    )


def forwards_self_rng_to_move(fn) -> bool:
    tree = ast.parse(textwrap.dedent(inspect.getsource(fn)))
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call) or call_name(node) != "resolve_move_action":
            continue
        if node.args and is_self_rng(node.args[0]):
            return True
        for keyword in node.keywords:
            if keyword.arg == "rng" and is_self_rng(keyword.value):
                return True
    return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    import auto_ptu.rules.battle_state as battle_state_module
    from auto_ptu.rules.battle_state import BattleState
    from auto_ptu.rules.controllers.phase_controller import PhaseController

    resolve_move_action = battle_state_module.resolve_move_action
    signature = inspect.signature(resolve_move_action)
    move_source = inspect.getsource(resolve_move_action)
    target_source = inspect.getsource(BattleState.resolve_move_targets)
    start_round_source = inspect.getsource(PhaseController.start_round)

    move_accepts_rng = "rng" in signature.parameters
    move_consumes_rng = "rng.randint" in move_source and "attack_hits(rng" in move_source
    target_forwards_self_rng = forwards_self_rng_to_move(BattleState.resolve_move_targets)
    target_feeds_move_action = "resolve_move_action" in target_source
    delayed_receives_battle = "resolve_delayed_hits" in start_round_source and "self.battle" in start_round_source

    # Fail loudly if Python changes ownership or forwarding. Java must then review
    # the lifecycle/RNG seam instead of silently preserving stale assumptions.
    assert move_accepts_rng
    assert move_consumes_rng
    assert target_forwards_self_rng
    assert target_feeds_move_action
    assert delayed_receives_battle

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\t".join(
            [
                "BATTLE_RNG_OWNERSHIP",
                "1" if move_accepts_rng else "0",
                "1" if move_consumes_rng else "0",
                "1" if target_forwards_self_rng else "0",
                "1" if target_feeds_move_action else "0",
                "1" if delayed_receives_battle else "0",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
