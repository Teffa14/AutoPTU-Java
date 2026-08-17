#!/usr/bin/env python3
"""Export offensive/defensive/speed stat behavior from pinned Python AutoPTU."""
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass, field
from pathlib import Path
from types import SimpleNamespace


@dataclass(frozen=True)
class Scenario:
    name: str
    operation: str
    category: str = "Physical"
    bases: dict[str, int] = field(default_factory=dict)
    stages: dict[str, int] = field(default_factory=dict)
    additive: dict[str, int] = field(default_factory=dict)
    scalar: dict[str, float] = field(default_factory=dict)
    post: dict[str, int] = field(default_factory=dict)
    statuses: tuple[str, ...] = ()
    abilities: tuple[str, ...] = ()
    exact_abilities: tuple[str, ...] = ()
    potent_override: bool = False
    ignore_positive_stage: bool = False


class FixturePokemon:
    def __init__(self, scenario: Scenario):
        base = {"atk": 10, "def": 10, "spatk": 10, "spdef": 10, "spd": 10}
        base.update(scenario.bases)
        self.spec = SimpleNamespace(
            atk=base["atk"], defense=base["def"], spatk=base["spatk"],
            spdef=base["spdef"], spd=base["spd"]
        )
        self._stages = {"atk": 0, "def": 0, "spatk": 0, "spdef": 0, "spd": 0}
        self._stages.update(scenario.stages)
        self.combat_stages = dict(self._stages)
        self._statuses = {x.lower() for x in scenario.statuses}
        self._abilities = {x.lower() for x in scenario.abilities}
        self._exact = {x.lower() for x in scenario.exact_abilities}
        self._potent_override = scenario.potent_override
        self.additive = dict(scenario.additive)
        self.scalar = dict(scenario.scalar)
        self.post = dict(scenario.post)

    def effective_combat_stage(self, stat: str) -> int:
        return self._stages.get(stat, 0)

    def has_status(self, name: str) -> bool:
        return str(name or "").strip().lower() in self._statuses

    def has_ability(self, name: str) -> bool:
        return str(name or "").strip().lower() in self._abilities or str(name or "").strip().lower() in self._exact

    def has_exact_ability(self, name: str) -> bool:
        return str(name or "").strip().lower() in self._exact

    def get_temporary_effects(self, name: str):
        if name == "potent_venom_poison_override" and self._potent_override:
            return [{}]
        return []


SCENARIOS = (
    Scenario("offense_physical_stage2", "offensive", bases={"atk": 12}, stages={"atk": 2}),
    Scenario("offense_modifiers_floor_order", "offensive", bases={"atk": 10}, stages={"atk": 1}, additive={"atk": 3}, scalar={"atk": 1.5}, post={"atk": 2}),
    Scenario("offense_power_shift_physical", "offensive", bases={"atk": 5, "def": 20}, stages={"def": 1}, statuses=("Power Shift",)),
    Scenario("offense_power_trick_physical", "offensive", bases={"atk": 5, "def": 18}, stages={"def": -1}, statuses=("Power Trick",)),
    Scenario("offense_power_shift_heavy", "offensive", bases={"def": 10}, statuses=("Power Shift",), exact_abilities=("Heavy Metal [Errata]",)),
    Scenario("offense_power_shift_light", "offensive", bases={"def": 10}, statuses=("Power Shift",), exact_abilities=("Light Metal [Errata]",)),
    Scenario("offense_power_shift_special", "offensive", category="Special", bases={"spatk": 5, "spdef": 20}, stages={"spdef": 1}, statuses=("Power Shift",)),
    Scenario("offense_flare_boost_burn", "offensive", category="Special", bases={"spatk": 12}, statuses=("Burned",), exact_abilities=("Flare Boost",)),
    Scenario("offense_ignore_positive", "offensive", bases={"atk": 12}, stages={"atk": 3}, ignore_positive_stage=True),
    Scenario("defense_burn_physical", "defensive", bases={"def": 18}, statuses=("Burned",)),
    Scenario("defense_wonder_room_physical", "defensive", bases={"def": 7, "spdef": 22}, statuses=("Wonder Room",)),
    Scenario("defense_power_shift_physical", "defensive", bases={"def": 5, "atk": 15}, stages={"atk": 1}, statuses=("Power Shift",)),
    Scenario("defense_poison_special", "defensive", category="Special", bases={"spdef": 20}, statuses=("Poisoned",)),
    Scenario("defense_potent_override", "defensive", category="Special", bases={"spdef": 20}, statuses=("Poisoned",), potent_override=True),
    Scenario("defense_wonder_special_heavy_light", "defensive", category="Special", bases={"def": 14, "spdef": 5}, statuses=("Wonder Room",), exact_abilities=("Heavy Metal [Errata]", "Light Metal [Errata]")),
    Scenario("speed_stage1", "speed", bases={"spd": 16}, stages={"spd": 1}),
    Scenario("speed_paralyzed", "speed", bases={"spd": 18}, statuses=("Paralyzed",)),
    Scenario("speed_quick_feet_paralyzed", "speed", bases={"spd": 16}, statuses=("Paralyzed",), abilities=("Quick Feet",)),
    Scenario("speed_heavy", "speed", bases={"spd": 10}, exact_abilities=("Heavy Metal [Errata]",)),
    Scenario("speed_light", "speed", bases={"spd": 10}, exact_abilities=("Light Metal [Errata]",)),
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    sys.path.insert(0, str(args.source_root.resolve()))

    from auto_ptu.rules import calculations

    original = {
        "trainer": calculations._trainer_stat_ace_bonus,
        "modifier": calculations._temporary_stat_modifier,
        "scalar": calculations._temporary_stat_scalar,
        "post": calculations._post_stage_stat_bonus,
        "errata": calculations._errata_attack_bonus,
        "exact": calculations.has_ability_exact,
    }
    results: list[tuple[str, str]] = []
    try:
        calculations._trainer_stat_ace_bonus = lambda _pokemon, _stat: 0
        calculations._temporary_stat_modifier = lambda pokemon, stat: int(pokemon.additive.get(stat, 0))
        calculations._temporary_stat_scalar = lambda pokemon, stat: float(pokemon.scalar.get(stat, 1.0))
        calculations._post_stage_stat_bonus = lambda pokemon, stat: int(pokemon.post.get(stat, 0))
        calculations._errata_attack_bonus = lambda _pokemon: 0
        calculations.has_ability_exact = lambda pokemon, name: pokemon.has_exact_ability(name)

        for scenario in SCENARIOS:
            pokemon = FixturePokemon(scenario)
            if scenario.operation == "offensive":
                value = calculations.offensive_stat(
                    pokemon, scenario.category,
                    ignore_positive_stage=scenario.ignore_positive_stage,
                )
            elif scenario.operation == "defensive":
                value = calculations.defensive_stat(
                    pokemon, scenario.category,
                    ignore_positive_stage=scenario.ignore_positive_stage,
                )
            elif scenario.operation == "speed":
                value = calculations.speed_stat(pokemon)
            else:
                raise RuntimeError(f"unknown operation: {scenario.operation}")
            results.append((scenario.name, str(int(value))))
    finally:
        calculations._trainer_stat_ace_bonus = original["trainer"]
        calculations._temporary_stat_modifier = original["modifier"]
        calculations._temporary_stat_scalar = original["scalar"]
        calculations._post_stage_stat_bonus = original["post"]
        calculations._errata_attack_bonus = original["errata"]
        calculations.has_ability_exact = original["exact"]

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("".join(f"{name}\t{value}\n" for name, value in results), encoding="utf-8")
    print(f"wrote {len(results)} Python stat-resolution fixtures to {args.output.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
