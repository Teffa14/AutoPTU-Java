#!/usr/bin/env python3
"""Freeze replacement initiative insertion behavior from the pinned Python runtime."""
from __future__ import annotations

import argparse
import inspect
import sys
import textwrap
import types
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.rules.battle_state import BattleState, InitiativeEntry

    source = textwrap.dedent(inspect.getsource(BattleState._insert_replacement_initiative))

    def entry(actor: str, total: int, roll: int = 10, speed: int = 10) -> InitiativeEntry:
        return InitiativeEntry(
            actor_id=actor,
            trainer_id=f"trainer-{actor}",
            speed=speed,
            trainer_modifier=0,
            roll=roll,
            total=total,
        )

    def run_case(
        name: str,
        order: list[InitiativeEntry],
        cursor: int,
        candidate: InitiativeEntry | None,
        allow_immediate: bool,
    ) -> tuple[str, list[str], int, list[str], int]:
        battle = BattleState.__new__(BattleState)
        battle.initiative_order = list(order)
        battle._initiative_index = cursor

        def resolve(_self, actor_id: str):
            if candidate is None or candidate.actor_id != actor_id:
                return None
            return candidate

        battle._initiative_entry_for_pokemon = types.MethodType(resolve, battle)
        before_order = [item.actor_id for item in battle.initiative_order]
        before_cursor = battle._initiative_index
        actor_id = candidate.actor_id if candidate is not None else "replacement"
        battle._insert_replacement_initiative(actor_id, allow_immediate=allow_immediate)
        after_order = [item.actor_id for item in battle.initiative_order]
        after_cursor = battle._initiative_index
        return name, before_order, before_cursor, after_order, after_cursor

    current = entry("current", 80)
    cases = [
        run_case(
            "duplicate_noop",
            [entry("fast", 100), entry("replacement", 90), current, entry("slow", 50)],
            2,
            entry("replacement", 90),
            False,
        ),
        run_case(
            "missing_entry_noop",
            [entry("fast", 100), current, entry("slow", 50)],
            1,
            None,
            False,
        ),
        run_case(
            "natural_future",
            [entry("fast", 100), current, entry("slow", 50)],
            1,
            entry("replacement", 70),
            False,
        ),
        run_case(
            "natural_past_no_immediate",
            [entry("fast", 100), current, entry("slow", 50)],
            1,
            entry("replacement", 90),
            False,
        ),
        run_case(
            "natural_past_immediate",
            [entry("fast", 100), current, entry("slow", 50)],
            1,
            entry("replacement", 90),
            True,
        ),
        run_case(
            "tie_break_roll",
            [entry("fast", 100), current, entry("slow", 50)],
            1,
            entry("replacement", 80, roll=20),
            True,
        ),
        run_case(
            "empty_round",
            [],
            -1,
            entry("replacement", 75),
            True,
        ),
    ]

    expected = {
        "duplicate_noop": (["fast", "replacement", "current", "slow"], 2),
        "missing_entry_noop": (["fast", "current", "slow"], 1),
        "natural_future": (["fast", "current", "replacement", "slow"], 1),
        "natural_past_no_immediate": (["fast", "replacement", "current", "slow"], 2),
        "natural_past_immediate": (["fast", "current", "replacement", "slow"], 1),
        "tie_break_roll": (["fast", "current", "replacement", "slow"], 1),
        "empty_round": (["replacement"], -1),
    }
    for name, _before_order, _before_cursor, after_order, after_cursor in cases:
        expected_order, expected_cursor = expected[name]
        if after_order != expected_order or after_cursor != expected_cursor:
            raise AssertionError(
                f"Pinned replacement initiative contract changed for {name}: "
                f"order={after_order}, cursor={after_cursor}"
            )

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        handle.write("SOURCE_BEGIN\n")
        for index, line in enumerate(source.splitlines(), start=1):
            handle.write(f"SOURCE\t{index}\t{line}\n")
        handle.write("SOURCE_END\n")
        for name, before_order, before_cursor, after_order, after_cursor in cases:
            handle.write(
                "CASE\t"
                f"{name}\t{','.join(before_order)}\t{before_cursor}\t"
                f"{','.join(after_order)}\t{after_cursor}\n"
            )
    print(output)


if __name__ == "__main__":
    main()
