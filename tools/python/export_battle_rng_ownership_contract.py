#!/usr/bin/env python3
"""Freeze the Python oracle's battle-scoped RNG ownership contract."""

from __future__ import annotations

import argparse
import inspect
import sys
from pathlib import Path


def safe_source(value) -> str:
    try:
        return inspect.getsource(value)
    except (OSError, TypeError):
        return ""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.rules.battle_state import BattleState
    from auto_ptu.rules.controllers.phase_controller import PhaseController

    rng_methods = sorted(
        name
        for name, method in inspect.getmembers(BattleState, predicate=inspect.isfunction)
        if "self.rng" in safe_source(method)
    )
    assignment_methods = sorted(
        name
        for name, method in inspect.getmembers(BattleState, predicate=inspect.isfunction)
        if "self.rng =" in safe_source(method) or "self.rng:" in safe_source(method)
    )

    move_action_source = safe_source(BattleState.resolve_move_action)
    target_source = safe_source(BattleState.resolve_move_targets)
    start_round_source = safe_source(PhaseController.start_round)

    move_uses_battle_rng = "self.rng" in move_action_source
    target_feeds_move_action = "resolve_move_action" in target_source
    delayed_receives_battle = "resolve_delayed_hits" in start_round_source and "self.battle" in start_round_source

    # Fail loudly if Python moves these responsibilities. Java must then review
    # the lifecycle/RNG boundary instead of preserving an obsolete assumption.
    assert rng_methods, "BattleState must expose methods that consume its RNG stream"
    assert move_uses_battle_rng, "resolve_move_action must consume BattleState.rng"
    assert target_feeds_move_action, "resolve_move_targets must feed resolve_move_action"
    assert delayed_receives_battle, "round-start delayed resolution must receive the BattleState owner"

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\t".join(
            [
                "BATTLE_RNG_OWNERSHIP",
                "1",
                ",".join(assignment_methods),
                ",".join(rng_methods),
                "1" if move_uses_battle_rng else "0",
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
