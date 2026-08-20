#!/usr/bin/env python3
"""Export parity fixtures for the stable base of _initiative_entry_for_pokemon()."""
from __future__ import annotations

import argparse
import importlib
import sys
from pathlib import Path
from types import SimpleNamespace


class PokemonStub:
    def __init__(self, actor_id: str, speed: int, trainer_id: str, statuses=(), effects=None):
        self.actor_id = actor_id
        self.resolved_speed = speed
        self.controller_id = trainer_id
        self.hp = 100
        self._statuses = {str(value).strip().lower() for value in statuses}
        self._effects = {str(key).strip().lower(): [dict(item) for item in values] for key, values in (effects or {}).items()}

    def has_status(self, name: str) -> bool:
        return str(name).strip().lower() in self._statuses

    def has_ability(self, name: str) -> bool:
        return False

    def get_temporary_effects(self, name: str):
        return list(self._effects.get(str(name).strip().lower(), []))

    def max_hp(self) -> int:
        return 100

    def hardened_initiative_bonus(self, battle) -> int:
        return 0


class BattleStub:
    pass


def run_case(battle_state_module, calculations, case):
    pokemon = PokemonStub(
        actor_id=case["actor_id"],
        speed=case["speed"],
        trainer_id=case["trainer_id"],
        statuses=case.get("statuses", ()),
        effects=case.get("effects", {}),
    )
    battle = BattleStub()
    battle.pokemon = {case["actor_id"]: pokemon}
    battle.trainers = {
        case["trainer_id"]: SimpleNamespace(initiative_modifier=case["trainer_modifier"])
    }
    battle.round = case["round"]
    battle.tailwind_teams = {"alpha"} if case.get("tailwind", False) else set()
    battle.terrain = {}
    battle.effective_weather = lambda: ""
    battle._is_actor_grounded = lambda ignored: True
    battle._team_for = lambda actor_id: "alpha"
    battle._rider_agility_training_doubled = lambda actor_id: False

    original_speed_stat = calculations.speed_stat
    original_exact = battle_state_module.has_ability_exact
    calculations.speed_stat = lambda ignored: case["speed"]
    battle_state_module.has_ability_exact = lambda ignored, name: False
    try:
        result = battle_state_module.BattleState._initiative_entry_for_pokemon(battle, case["actor_id"])
    finally:
        calculations.speed_stat = original_speed_stat
        battle_state_module.has_ability_exact = original_exact

    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    sys.path.insert(0, str(source_root))
    battle_state = importlib.import_module("auto_ptu.rules.battle_state")
    calculations = importlib.import_module("auto_ptu.rules.calculations")

    cases = [
        dict(name="basic", actor_id="p1", trainer_id="t1", speed=12, trainer_modifier=3, round=2),
        dict(name="bashed", actor_id="p1", trainer_id="t1", speed=12, trainer_modifier=3, round=2, statuses=("Bashed",)),
        dict(name="tailwind", actor_id="p1", trainer_id="t1", speed=12, trainer_modifier=3, round=2, tailwind=True),
        dict(name="active_bonus", actor_id="p1", trainer_id="t1", speed=12, trainer_modifier=3, round=2,
             effects={"initiative_bonus": [{"amount": 4}, {"amount": "-2", "expires_round": 2}]}),
        dict(name="expired_bonus", actor_id="p1", trainer_id="t1", speed=12, trainer_modifier=3, round=3,
             effects={"initiative_bonus": [{"amount": 7, "expires_round": 2}, {"amount": 2, "expires_round": 3}]}),
        dict(name="invalid_payload", actor_id="p1", trainer_id="t1", speed=12, trainer_modifier=3, round=3,
             effects={"initiative_bonus": [{"amount": "bad"}, {"amount": 5, "expires_round": "bad"}]}),
        dict(name="zero_until_turn", actor_id="p1", trainer_id="t1", speed=12, trainer_modifier=3, round=2,
             tailwind=True, effects={"initiative_bonus": [{"amount": 4}], "initiative_zero_until_turn": [{}]}),
    ]

    rows = ["name\tactor_id\ttrainer_id\tresolved_speed\ttrainer_modifier\tbashed\ttailwind\tround\ttemporary_effects\tzero_until_turn\texpected_speed\texpected_roll\texpected_total"]
    for case in cases:
        result = run_case(battle_state, calculations, case)
        effects = case.get("effects", {})
        encoded = []
        for effect_name, entries in effects.items():
            for entry in entries:
                amount = entry.get("amount", "")
                expires = entry.get("expires_round", "")
                encoded.append(f"{effect_name}:{amount}:{expires}")
        zero = bool(effects.get("initiative_zero_until_turn"))
        rows.append("\t".join([
            case["name"],
            case["actor_id"],
            case["trainer_id"],
            str(case["speed"]),
            str(case["trainer_modifier"]),
            "1" if "Bashed" in case.get("statuses", ()) else "0",
            "1" if case.get("tailwind", False) else "0",
            str(case["round"]),
            ";".join(encoded),
            "1" if zero else "0",
            str(result.speed),
            str(result.roll),
            str(result.total),
        ]))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote {len(cases)} Pokemon initiative-entry fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
