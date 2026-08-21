#!/usr/bin/env python3
"""Freeze the Python oracle's battle-scoped RNG ownership contract."""

from __future__ import annotations

import argparse
import ast
import inspect
import sys
import textwrap
from collections import deque
from pathlib import Path


def safe_source(value) -> str:
    try:
        return inspect.getsource(value)
    except (OSError, TypeError):
        return ""


def call_names(source: str) -> set[str]:
    if not source:
        return set()
    tree = ast.parse(textwrap.dedent(source))
    result: set[str] = set()
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call):
            continue
        target = node.func
        if isinstance(target, ast.Name):
            result.add(target.id)
        elif isinstance(target, ast.Attribute):
            result.add(target.attr)
    return result


def reachable_rng_users(root_function, module_functions: dict[str, object]) -> tuple[list[str], list[str]]:
    queue: deque[tuple[str, object]] = deque([(root_function.__name__, root_function)])
    visited: set[str] = set()
    rng_users: list[str] = []

    while queue:
        name, function = queue.popleft()
        if name in visited:
            continue
        visited.add(name)
        source = safe_source(function)
        if ".rng" in source:
            rng_users.append(name)
        for called in sorted(call_names(source)):
            candidate = module_functions.get(called)
            if candidate is not None and called not in visited:
                queue.append((called, candidate))

    return sorted(rng_users), sorted(visited)


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

    module_functions = {
        name: function
        for name, function in inspect.getmembers(battle_state_module, predicate=inspect.isfunction)
    }
    reachable_rng, visited = reachable_rng_users(resolve_move_action, module_functions)
    all_rng_users = sorted(
        {
            name
            for name, function in module_functions.items()
            if ".rng" in safe_source(function)
        }
        | {
            name
            for name, method in inspect.getmembers(BattleState, predicate=inspect.isfunction)
            if ".rng" in safe_source(method)
        }
    )

    move_reaches_battle_rng = bool(reachable_rng)
    target_feeds_move_action = "resolve_move_action" in target_source
    delayed_receives_battle = "resolve_delayed_hits" in start_round_source and "self.battle" in start_round_source

    if not move_reaches_battle_rng:
        print("RNG_CONTRACT_DIAGNOSTIC")
        print("resolve_move_action signature:", inspect.signature(resolve_move_action))
        print("direct calls:", ",".join(sorted(call_names(move_action_source))))
        print("visited module functions:", ",".join(visited))
        print("known rng users:", ",".join(all_rng_users))
        print("resolve_move_action source:")
        print(move_action_source)

    # Fail loudly if Python moves these responsibilities. Java must then review
    # the lifecycle/RNG boundary instead of preserving an obsolete assumption.
    assert all_rng_users, "battle rules must expose functions that consume the battle RNG stream"
    assert move_reaches_battle_rng, "resolve_move_action must reach a battle RNG consumer"
    assert target_feeds_move_action, "resolve_move_targets must feed resolve_move_action"
    assert delayed_receives_battle, "round-start delayed resolution must receive the BattleState owner"

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\t".join(
            [
                "BATTLE_RNG_OWNERSHIP",
                "1",
                ",".join(reachable_rng),
                ",".join(all_rng_users),
                "1" if move_reaches_battle_rng else "0",
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
