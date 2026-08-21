#!/usr/bin/env python3
"""Export end-to-end ordering fixtures for Python BattleState._build_initiative_order()."""
from __future__ import annotations

import argparse
import importlib
import sys
from pathlib import Path


class TrainerStub:
    def __init__(self, trainer_id: str, speed: int, bonus: int = 0):
        self.identifier = trainer_id
        self.team = trainer_id
        self.speed = speed
        self._bonus = bonus

    def initiative_bonus(self):
        return self._bonus


class PokemonStub:
    def __init__(self, controller_id: str, *, active=True, fainted=False, parental=False, effects=None, abilities=None):
        self.controller_id = controller_id
        self.active = active
        self.fainted = fainted
        self._effects = {
            str(name).strip().lower(): [dict(entry) for entry in entries]
            for name, entries in (effects or {}).items()
        }
        if parental:
            self._effects.setdefault("parental_bond_child", [{}])
        self.abilities = list(abilities or [])
        self.removed = []

    def get_temporary_effects(self, name: str):
        return list(self._effects.get(str(name).strip().lower(), []))

    def remove_temporary_effect(self, name: str):
        key = str(name).strip().lower()
        self.removed.append(key)
        self._effects.pop(key, None)


class BattleStub:
    def __init__(self, battle_state, case):
        self.round = case["round"]
        self.tailwind_teams = set(case.get("tailwind_teams", []))
        self.trainers = {
            spec["id"]: TrainerStub(spec["id"], spec["speed"], spec.get("bonus", 0))
            for spec in case.get("trainers", [])
        }
        self.pokemon = {}
        self._entries = {}
        for spec in case.get("pokemon", []):
            actor_id = spec["id"]
            self.pokemon[actor_id] = PokemonStub(
                spec["trainer"],
                active=spec.get("active", True),
                fainted=spec.get("fainted", False),
                parental=spec.get("parental", False),
                effects=spec.get("effects", {}),
                abilities=spec.get("abilities", []),
            )
            if not spec.get("no_entry", False):
                self._entries[actor_id] = battle_state.InitiativeEntry(
                    actor_id=actor_id,
                    trainer_id=spec["trainer"],
                    speed=spec.get("speed", spec["total"]),
                    trainer_modifier=spec.get("modifier", 0),
                    roll=0,
                    total=spec["total"],
                )
        self._trick_room = case.get("trick_room", False)
        self._league = case.get("league", False)

    def _trainer_initiative_speed(self, trainer_id: str):
        return self.trainers[trainer_id].speed

    def _initiative_entry_for_pokemon(self, actor_id: str):
        return self._entries.get(actor_id)

    def _room_effect_active(self, name: str):
        return self._trick_room and str(name).strip().lower() == "trick room"

    def is_league_battle(self):
        return self._league


def cases():
    return [
        {
            "name": "normal_mixed_order",
            "round": 2,
            "trainers": [{"id": "t1", "speed": 18}],
            "pokemon": [
                {"id": "p1", "trainer": "t1", "speed": 30, "total": 30},
                {"id": "p2", "trainer": "t1", "speed": 20, "total": 20},
            ],
        },
        {
            "name": "trick_room_mixed_order",
            "round": 2,
            "trick_room": True,
            "trainers": [{"id": "t1", "speed": 18}],
            "pokemon": [
                {"id": "p1", "trainer": "t1", "speed": 30, "total": 30},
                {"id": "p2", "trainer": "t1", "speed": 20, "total": 20},
            ],
        },
        {
            "name": "league_trainers_stay_first",
            "round": 2,
            "league": True,
            "trainers": [
                {"id": "t1", "speed": 5},
                {"id": "t2", "speed": 50},
            ],
            "pokemon": [
                {"id": "p1", "trainer": "t1", "speed": 100, "total": 100},
                {"id": "p2", "trainer": "t2", "speed": 10, "total": 10},
            ],
        },
        {
            "name": "filters_ineligible_pokemon",
            "round": 2,
            "pokemon": [
                {"id": "p1", "trainer": "t1", "speed": 20, "total": 20},
                {"id": "p2", "trainer": "t1", "speed": 90, "total": 90, "fainted": True},
                {"id": "p3", "trainer": "t1", "speed": 80, "total": 80, "active": False},
                {"id": "p4", "trainer": "t1", "speed": 70, "total": 70, "parental": True},
                {"id": "p5", "trainer": "t1", "speed": 60, "total": 60, "no_entry": True},
            ],
        },
        {
            "name": "round_modifiers_and_cleanup",
            "round": 3,
            "pokemon": [
                {"id": "p1", "trainer": "t1", "speed": 20, "total": 20,
                 "effects": {"rocket_initiative": [{"round": 3}]}},
                {"id": "p2", "trainer": "t1", "speed": 25, "total": 25,
                 "effects": {"initiative_penalty": [
                     {"amount": -8, "source_id": "enemy", "expires_round": 2},
                     {"amount": 4, "source_id": "enemy", "expires_round": 3},
                 ]}},
            ],
        },
    ]


def encode_entries(entries):
    return ";".join(f"{entry.actor_id}|{entry.total}" for entry in entries)


def encode_removed(battle):
    chunks = []
    for actor_id, pokemon in battle.pokemon.items():
        unique = list(dict.fromkeys(pokemon.removed))
        if unique:
            chunks.append(f"{actor_id}:{','.join(unique)}")
    return ";".join(chunks)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    sys.path.insert(0, str(source_root))
    battle_state = importlib.import_module("auto_ptu.rules.battle_state")
    original_exact = battle_state.has_ability_exact
    battle_state.has_ability_exact = lambda pokemon, name: any(
        str(ability).strip().lower() == str(name).strip().lower()
        for ability in getattr(pokemon, "abilities", [])
    )

    rows = ["name\tround\ttrick_room\tleague\texpected_entries\tremoved"]
    try:
        for case in cases():
            battle = BattleStub(battle_state, case)
            entries = battle_state.BattleState._build_initiative_order(battle)
            rows.append("\t".join([
                case["name"],
                str(case["round"]),
                "1" if case.get("trick_room", False) else "0",
                "1" if case.get("league", False) else "0",
                encode_entries(entries),
                encode_removed(battle),
            ]))
    finally:
        battle_state.has_ability_exact = original_exact

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote {len(rows) - 1} initiative order assembly fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
