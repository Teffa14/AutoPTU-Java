#!/usr/bin/env python3
"""Export parity fixtures for initiative bonuses applied after Speed resolution."""
from __future__ import annotations

import argparse
import importlib
import sys
from pathlib import Path
from types import SimpleNamespace


class PokemonStub:
    def __init__(self, speed: int, abilities, agility_training: bool, hardened_bonus: int):
        self.actor_id = "p1"
        self.controller_id = "t1"
        self.hp = 100
        self._abilities = [str(value) for value in abilities]
        self._agility_training = bool(agility_training)
        self._hardened_bonus = int(hardened_bonus)
        self.fainted = False
        self.active = True

    def has_status(self, name: str) -> bool:
        return False

    def has_ability(self, name: str) -> bool:
        target = str(name).strip().lower()
        return any(str(value).strip().lower() == target for value in self._abilities)

    def get_temporary_effects(self, name: str):
        if name == "agility_training" and self._agility_training:
            return [{"source": "fixture"}]
        return []

    def max_hp(self) -> int:
        return 100

    def hardened_initiative_bonus(self, battle) -> int:
        return self._hardened_bonus


class BattleStub:
    pass


def run_case(battle_state_module, calculations, case):
    pokemon = PokemonStub(
        speed=case["speed"],
        abilities=case.get("abilities", ()),
        agility_training=case.get("agility", False),
        hardened_bonus=case.get("hardened", 0),
    )
    battle = BattleStub()
    battle.pokemon = {"p1": pokemon}
    battle.trainers = {"t1": SimpleNamespace(initiative_modifier=0)}
    battle.round = 2
    battle.tailwind_teams = set()
    battle.terrain = {"name": ""}
    battle.effective_weather = lambda: ""
    battle._is_actor_grounded = lambda ignored: True
    battle._team_for = lambda actor_id: "alpha"
    battle._rider_agility_training_doubled = lambda actor_id: bool(case.get("rider", False))

    original_speed_stat = calculations.speed_stat
    original_exact = battle_state_module.has_ability_exact
    calculations.speed_stat = lambda ignored: case["speed"]
    battle_state_module.has_ability_exact = lambda ignored, name: any(
        str(value).strip().lower() == str(name).strip().lower()
        for value in case.get("abilities", ())
    )
    try:
        result = battle_state_module.BattleState._initiative_entry_for_pokemon(battle, "p1")
    finally:
        calculations.speed_stat = original_speed_stat
        battle_state_module.has_ability_exact = original_exact
    return result


def encode_abilities(values) -> str:
    return ",".join(str(value) for value in values)


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
        dict(name="baseline", speed=11),
        dict(name="early_bird_errata_odd_speed", speed=11, abilities=("Early Bird [Errata]",)),
        dict(name="early_bird_base_not_errata", speed=11, abilities=("Early Bird",)),
        dict(name="early_bird_zero_speed", speed=0, abilities=("Early Bird [Errata]",)),
        dict(name="agility_training", speed=11, agility=True),
        dict(name="agility_training_rider_doubled", speed=11, agility=True, rider=True),
        dict(name="rider_without_agility", speed=11, rider=True),
        dict(name="hardened_initiative", speed=11, hardened=3),
        dict(name="hardened_negative_passthrough", speed=11, hardened=-2),
        dict(name="stacked_all", speed=11, abilities=("Early Bird [Errata]",), agility=True, rider=True, hardened=3),
    ]

    rows = ["name\tspeed\tabilities\tagility\trider_doubled\thardened_bonus\texpected_bonus"]
    for case in cases:
        result = run_case(battle_state, calculations, case)
        expected_bonus = result.total - result.speed
        rows.append("\t".join([
            case["name"],
            str(case["speed"]),
            encode_abilities(case.get("abilities", ())),
            "1" if case.get("agility", False) else "0",
            "1" if case.get("rider", False) else "0",
            str(case.get("hardened", 0)),
            str(expected_bonus),
        ]))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote {len(cases)} initiative additional bonus fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
