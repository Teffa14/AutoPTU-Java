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

    import auto_ptu.rules.battle_state as battle_state_module
    from auto_ptu.rules.battle_state import BattleState
    from auto_ptu.rules.controllers.phase_controller import PhaseController

    resolve_move_action = getattr(battle_state_module, "resolve_move_action")
    move_action_source = safe_source(resolve_move_action)
    target_source = safe_source(BattleState.resolve_move_targets)
    start_round_source = safe_source(PhaseController.start_round)

    class_rng_methods = sorted(
        name
        for name, method in inspect.getmembers(BattleState, predicate=inspect.isfunction)
        if ".rng" in safe_source(method)
    )
    module_rng_functions = sorted(
        name
        for name, function in inspect.getmembers(battle_state_module, predicate=inspect.isfunction)
        if ".rng" in safe_source(function)
    )
    rng_users = sorted(set(class_rng_methods + module_rng_functions))

    move_uses_battle_rng = ".rng" in move_action_source
    target_feeds_move_action = "resolve_move_action" in target_source
    delayed_receives_battle = "resolve_delayed_hits" in start_round_source and "self.battle" in start_round_source

    # Fail loudly if Python moves these responsibilities. Java must then review
    # the lifecycle/RNG boundary instead of preserving an obsolete assumption.
    assert rng_users, "battle rules must expose functions that consume the battle RNG stream"
    assert move_uses_battle_rng, "resolve_move_action must consume the battle-owned RNG"
    assert target_feeds_move_action, "resolve_move_targets must feed resolve_move_action"
    assert delayed_receives_battle, "round-start delayed resolution must receive the BattleState owner"

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\t".join(
            [
                "BATTLE_RNG_OWNERSHIP",
                "1",
                "",
                ",".join(rng_users),
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
