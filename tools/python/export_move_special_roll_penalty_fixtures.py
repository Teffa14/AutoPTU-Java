#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))
    from auto_ptu.rules.battle_state import BattleState

    class FakeBattle:
        def __init__(self, round_number: int) -> None:
            self.round = round_number

    class FakePokemon:
        def __init__(self, entries: list[dict[str, object]]) -> None:
            self.temporary_effects = [dict(entry) for entry in entries]

        def get_temporary_effects(self, _name: str):
            return list(self.temporary_effects)

    cases = [
        ("baseline", 5, []),
        ("stacked", 5, [{"amount": 2}, {"amount": 3}]),
        ("numeric_string", 5, [{"amount": "4"}]),
        ("invalid_amount", 5, [{"amount": "bad"}, {"amount": 2}]),
        ("negative_clamp", 5, [{"amount": -5}, {"amount": 2}]),
        ("same_round_kept", 5, [{"amount": 4, "expires_round": 5}]),
        ("expired_removed", 5, [{"amount": 7, "expires_round": 4}, {"amount": 2}]),
    ]

    rows: list[str] = []
    for case_id, round_number, entries in cases:
        pokemon = FakePokemon(entries)
        penalty = BattleState._roll_penalty(FakeBattle(round_number), pokemon)
        rows.append(f"{case_id}\t{penalty}\t{len(pokemon.temporary_effects)}")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
