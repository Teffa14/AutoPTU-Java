#!/usr/bin/env python3
"""Export parity fixtures for initiative-time Speed ability multipliers."""
from __future__ import annotations

import argparse
import importlib
import sys
from pathlib import Path
from types import SimpleNamespace


class PokemonStub:
    def __init__(self, actor_id: str, speed: int, hp, max_hp: int, trainer_id: str, abilities):
        self.actor_id = actor_id
        self.resolved_speed = speed
        self.controller_id = trainer_id
        self.hp = hp
        self._max_hp = max_hp
        self._abilities = [str(value) for value in abilities]
        self.fainted = False
        self.active = True

    def has_status(self, name: str) -> bool:
        return False

    def has_ability(self, name: str) -> bool:
        target = str(name).strip().lower()
        for ability in self._abilities:
            candidate = ability.strip().lower()
            if candidate == target or candidate == target + " [errata]":
                return True
        return False

    def get_temporary_effects(self, name: str):
        return []

    def max_hp(self) -> int:
        return self._max_hp

    def hardened_initiative_bonus(self, battle) -> int:
        return 0


class BattleStub:
    pass


def run_case(battle_state_module, calculations, case):
    pokemon = PokemonStub(
        actor_id="p1",
        speed=case["speed"],
        hp=case["hp"],
        max_hp=case["max_hp"],
        trainer_id="t1",
        abilities=case.get("abilities", ()),
    )
    battle = BattleStub()
    battle.pokemon = {"p1": pokemon}
    battle.trainers = {"t1": SimpleNamespace(initiative_modifier=0)}
    battle.round = 2
    battle.tailwind_teams = set()
    battle.terrain = {"name": case.get("terrain", "")}
    battle.effective_weather = lambda: case.get("weather", "")
    battle._is_actor_grounded = lambda ignored: bool(case.get("grounded", True))
    battle._team_for = lambda actor_id: "alpha"
    battle._rider_agility_training_doubled = lambda actor_id: False

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
        dict(name="baseline", speed=12, hp=100, max_hp=100),
        dict(name="slush_hail", speed=12, hp=100, max_hp=100, weather="Hailing", abilities=("Slush Rush",)),
        dict(name="slush_low_hp", speed=12, hp=50, max_hp=100, abilities=("Slush Rush",)),
        dict(name="slush_above_half", speed=12, hp=51, max_hp=100, abilities=("Slush Rush",)),
        dict(name="surge_electric_grounded", speed=12, hp=100, max_hp=100, terrain="Electric Terrain", grounded=True, abilities=("Surge Surfer",)),
        dict(name="surge_electric_airborne", speed=12, hp=100, max_hp=100, terrain="Electric Terrain", grounded=False, abilities=("Surge Surfer",)),
        dict(name="surge_airborne_low_hp", speed=12, hp=50, max_hp=100, terrain="Electric Terrain", grounded=False, abilities=("Surge Surfer",)),
        dict(name="chlorophyll_errata_sun", speed=12, hp=100, max_hp=100, weather="Harsh Sunlight", abilities=("Chlorophyll [Errata]",)),
        dict(name="chlorophyll_base_not_errata", speed=12, hp=100, max_hp=100, weather="Sunny", abilities=("Chlorophyll",)),
        dict(name="chlorophyll_errata_low_hp", speed=12, hp=50, max_hp=100, abilities=("Chlorophyll [Errata]",)),
        dict(name="null_hp_no_fallback", speed=12, hp=None, max_hp=100, abilities=("Slush Rush",)),
        dict(name="stacked_slush_surge", speed=12, hp=100, max_hp=100, weather="Hail", terrain="Electric", grounded=True, abilities=("Slush Rush", "Surge Surfer")),
    ]

    rows = ["name\tbase_speed\thp\tmax_hp\tweather\tterrain\tgrounded\tabilities\texpected_speed"]
    for case in cases:
        result = run_case(battle_state, calculations, case)
        rows.append("\t".join([
            case["name"],
            str(case["speed"]),
            "" if case["hp"] is None else str(case["hp"]),
            str(case["max_hp"]),
            case.get("weather", ""),
            case.get("terrain", ""),
            "1" if case.get("grounded", True) else "0",
            encode_abilities(case.get("abilities", ())),
            str(result.speed),
        ]))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote {len(cases)} initiative Speed ability fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
