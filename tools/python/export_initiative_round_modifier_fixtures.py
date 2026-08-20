#!/usr/bin/env python3
"""Export parity fixtures for round-scoped modifiers in _build_initiative_order()."""
from __future__ import annotations

import argparse
import importlib
import sys
from pathlib import Path


class PokemonStub:
    def __init__(self, actor_id: str, controller_id: str, effects):
        self.actor_id = actor_id
        self.controller_id = controller_id
        self.fainted = False
        self.active = True
        self._effects = {
            str(name).strip().lower(): [dict(entry) for entry in entries]
            for name, entries in effects.items()
        }
        self.removed = []

    def get_temporary_effects(self, name: str):
        return list(self._effects.get(str(name).strip().lower(), []))

    def remove_temporary_effect(self, name: str):
        key = str(name).strip().lower()
        self.removed.append(key)
        self._effects.pop(key, None)


class BattleStub:
    def __init__(self, battle_state, case, pokemon):
        self.trainers = {}
        self.pokemon = {case["actor_id"]: pokemon}
        self.round = case["round"]
        self._entry = battle_state.InitiativeEntry(
            actor_id=case["actor_id"],
            trainer_id=case["trainer_id"],
            speed=case.get("speed", 10),
            trainer_modifier=case.get("trainer_modifier", 0),
            roll=0,
            total=case["base_total"],
        )

    def _initiative_entry_for_pokemon(self, actor_id: str):
        return self._entry

    def in_trick_room(self) -> bool:
        return False

    def is_league_battle(self) -> bool:
        return False


def encode_effects(effects) -> str:
    encoded = []
    for name, entries in effects.items():
        for entry in entries:
            parts = [str(name).strip().lower()]
            for key in ("round", "expires_round", "amount", "source_id"):
                if key in entry:
                    parts.append(f"{key}={entry[key]}")
            encoded.append("|".join(parts))
    return ";".join(encoded)


def run_case(battle_state, case):
    pokemon = PokemonStub(case["actor_id"], case["trainer_id"], case.get("effects", {}))
    battle = BattleStub(battle_state, case, pokemon)
    original_exact = battle_state.has_ability_exact
    battle_state.has_ability_exact = lambda ignored, name: (
        case.get("inner_focus", False) and str(name).strip().lower() == "inner focus [errata]"
    )
    try:
        entries = battle_state.BattleState._build_initiative_order(battle)
    finally:
        battle_state.has_ability_exact = original_exact
    return entries[0], pokemon.removed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    sys.path.insert(0, str(source_root))
    battle_state = importlib.import_module("auto_ptu.rules.battle_state")

    cases = [
        dict(name="basic", actor_id="p1", trainer_id="t1", base_total=20, round=2),
        dict(name="rocket_current", actor_id="p1", trainer_id="t1", base_total=20, round=2,
             effects={"rocket_initiative": [{"round": 2}]}),
        dict(name="rocket_future", actor_id="p1", trainer_id="t1", base_total=20, round=2,
             effects={"rocket_initiative": [{"round": 3}]}),
        dict(name="rocket_expired_then_current_snapshot", actor_id="p1", trainer_id="t1", base_total=20, round=2,
             effects={"rocket_initiative": [{"round": 1}, {"round": 2}]}),
        dict(name="penalty_external", actor_id="p1", trainer_id="t1", base_total=20, round=2,
             effects={"initiative_penalty": [{"amount": -4, "source_id": "enemy", "expires_round": 2}]}),
        dict(name="penalty_external_inner_focus", actor_id="p1", trainer_id="t1", base_total=20, round=2,
             inner_focus=True,
             effects={"initiative_penalty": [{"amount": -4, "source_id": "enemy", "expires_round": 2}]}),
        dict(name="penalty_self_inner_focus", actor_id="p1", trainer_id="t1", base_total=20, round=2,
             inner_focus=True,
             effects={"initiative_penalty": [{"amount": -4, "source_id": "p1", "expires_round": 2}]}),
        dict(name="positive_inner_focus", actor_id="p1", trainer_id="t1", base_total=20, round=2,
             inner_focus=True,
             effects={"initiative_penalty": [{"amount": 3, "source_id": "enemy", "expires_round": 2}]}),
        dict(name="penalty_expired_then_active_snapshot", actor_id="p1", trainer_id="t1", base_total=20, round=2,
             effects={"initiative_penalty": [
                 {"amount": -9, "source_id": "enemy", "expires_round": 1},
                 {"amount": 3, "source_id": "enemy", "expires_round": 2},
             ]}),
        dict(name="combined", actor_id="p1", trainer_id="t1", base_total=20, round=2,
             effects={
                 "rocket_initiative": [{"round": 2}],
                 "initiative_penalty": [{"amount": -5, "source_id": "enemy", "expires_round": 2}],
             }),
    ]

    rows = ["name\tactor_id\ttrainer_id\tbase_total\tround\tinner_focus\ttemporary_effects\texpected_total\tremoved"]
    for case in cases:
        entry, removed = run_case(battle_state, case)
        rows.append("\t".join([
            case["name"],
            case["actor_id"],
            case["trainer_id"],
            str(case["base_total"]),
            str(case["round"]),
            "1" if case.get("inner_focus", False) else "0",
            encode_effects(case.get("effects", {})),
            str(entry.total),
            ",".join(dict.fromkeys(removed)),
        ]))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote {len(cases)} initiative round-modifier fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
