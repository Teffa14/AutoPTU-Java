#!/usr/bin/env python3
"""Execute pinned Python Follow Me/Foresight ROUND_START expiry as an oracle."""
from __future__ import annotations

import argparse
import importlib
import sys
from pathlib import Path


class PokemonStub:
    def __init__(self, effects):
        self.temporary_effects = [dict(entry) for entry in effects]

    def get_temporary_effects(self, kind):
        normalized = str(kind or "").lower()
        return [
            entry
            for entry in self.temporary_effects
            if isinstance(entry, dict) and entry.get("kind") == normalized
        ]

    def remove_temporary_effect(self, kind):
        normalized = str(kind or "").lower()
        for idx, entry in enumerate(self.temporary_effects):
            if isinstance(entry, dict) and entry.get("kind") == normalized:
                self.temporary_effects.pop(idx)
                return True
        return False


class BattleStub:
    def __init__(self, round_number, effects):
        self.round = round_number
        self.pokemon = {"alpha": PokemonStub(effects)}


def effect(kind, until_round=None, tag=""):
    entry = {"kind": kind}
    if until_round is not None:
        entry["until_round"] = until_round
    if tag:
        entry["tag"] = tag
    return entry


def cases():
    return [
        {
            "name": "ordinary_expiry_and_equal_round_preservation",
            "round": 4,
            "effects": [
                effect("follow_me", 2, "expired"),
                effect("follow_me", 4, "equal"),
                effect("foresight", 1, "expired"),
                effect("other", 0, "unrelated"),
            ],
        },
        {
            "name": "missing_and_invalid_until_round_are_preserved",
            "round": 6,
            "effects": [
                effect("follow_me", None, "missing"),
                effect("follow_me", "bad", "invalid"),
                effect("foresight", " 5 ", "numeric_string"),
            ],
        },
        {
            "name": "snapshot_iteration_removes_first_live_duplicate",
            "round": 3,
            "effects": [
                effect("follow_me", 9, "unexpired_first"),
                effect("follow_me", 1, "expired_second"),
            ],
        },
        {
            "name": "multiple_expired_duplicates_are_all_removed",
            "round": 5,
            "effects": [
                effect("foresight", 1, "first"),
                effect("foresight", 2, "second"),
                effect("foresight", 5, "equal"),
            ],
        },
    ]


def encode(entries):
    encoded = []
    for entry in entries:
        kind = str(entry.get("kind", ""))
        until = entry.get("until_round", "")
        tag = str(entry.get("tag", ""))
        encoded.append(f"{kind}~{until}~{tag}")
    return ";".join(encoded)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    sys.path.insert(0, str(source_root))
    battle_state = importlib.import_module("auto_ptu.rules.battle_state")

    rows = ["name\tround\tinput\toutput"]
    for case in cases():
        battle = BattleStub(case["round"], case["effects"])
        input_state = encode(battle.pokemon["alpha"].temporary_effects)
        battle_state.BattleState._clear_expired_follow_me(battle)
        battle_state.BattleState._clear_expired_foresight(battle)
        output_state = encode(battle.pokemon["alpha"].temporary_effects)
        rows.append("\t".join([case["name"], str(case["round"]), input_state, output_state]))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote {len(rows) - 1} round temporary-expiry fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
