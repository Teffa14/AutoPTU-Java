#!/usr/bin/env python3
"""Export invariant damage-pipeline fixtures from pinned Python resolve_move_action."""
from __future__ import annotations

import argparse
import random
import sys
from dataclasses import dataclass
from pathlib import Path
from types import SimpleNamespace


@dataclass(frozen=True)
class Scenario:
    name: str
    seed: int
    db: int
    attack: int
    defense: int
    crit: bool = False
    sniper: bool = False
    move_type: str = "Fire"
    defender_types: tuple[str, ...] = ("Normal",)
    modifiers: tuple[tuple[str, float], ...] = ()


class FixturePokemon:
    def __init__(self, *, types=("Normal",), sniper=False):
        self.spec = SimpleNamespace(types=list(types), level=20)
        self._sniper = sniper
        self.combat_stages = {}
        self.position = None
        self.battle = None
        self.hp = 100
        self.statuses = []

    def has_ability(self, name: str) -> bool:
        return self._sniper and str(name or "").strip().lower() == "sniper"

    def has_status(self, _name: str) -> bool:
        return False

    def get_temporary_effects(self, _name: str):
        return []

    def is_trainer_combatant(self) -> bool:
        return False

    def has_trainer_feature(self, _name: str) -> bool:
        return False

    def max_hp(self) -> int:
        return 100


SCENARIOS = (
    Scenario("db2_neutral", 1, 2, 10, 5),
    Scenario("db6_neutral", 42, 6, 12, 7),
    Scenario("db10_neutral", 1234, 10, 25, 14),
    Scenario("defense_floor_zero", 7, 2, 1, 999),
    Scenario("critical_db5", 99, 5, 10, 5, crit=True),
    Scenario("critical_sniper_db8", 5, 8, 10, 5, crit=True, sniper=True),
    Scenario("fire_vs_grass", 81, 7, 20, 8, defender_types=("Grass",)),
    Scenario("fire_vs_water", 81, 7, 20, 8, defender_types=("Water",)),
    Scenario("fire_vs_grass_ice", 91, 7, 20, 8, defender_types=("Grass", "Ice")),
    Scenario("fire_vs_water_dragon", 91, 7, 20, 8, defender_types=("Water", "Dragon")),
    Scenario("electric_immunity_ground", 27, 8, 18, 8, move_type="Electric", defender_types=("Ground",)),
    Scenario("flat_then_scalar", 314, 4, 10, 5, modifiers=(("flat:3", 3.0), ("scalar:0.5", 0.5), ("scalar:1.5", 1.5))),
    Scenario("scalar_rounding_order", 315, 9, 13, 9, modifiers=(("scalar:0.66", 0.66), ("scalar:1.5", 1.5))),
    Scenario("crit_with_modifiers", 888, 11, 22, 12, crit=True, modifiers=(("flat:5", 5.0), ("scalar:0.5", 0.5))),
)


def modifier(kind_slug: str, value: float, calculations):
    kind, _, token = kind_slug.partition(":")
    if kind == "flat":
        return calculations.AttackModifier(slug=f"fixture-flat-{token}", kind="damage_flat", value=value)
    if kind == "scalar":
        return calculations.AttackModifier(slug=f"fixture-scalar-{token}", kind="damage_scalar", value=value)
    raise RuntimeError(f"unknown modifier kind: {kind_slug}")


def encode(result: dict) -> str:
    n, s, p = result.get("db_components") or (0, 0, 0)
    return ",".join((
        str(int(result.get("effective_db") or 0)),
        f"{int(n)}d{int(s)}+{int(p)}",
        str(int(result.get("damage_roll") or 0)),
        str(int(result.get("crit_extra_roll") or 0)),
        str(int(result.get("attack_value") or 0)),
        str(int(result.get("defense_value") or 0)),
        str(int(result.get("pre_type_damage") or 0)),
        f"{float(result.get('type_multiplier') or 0.0):.6f}",
        str(int(result.get("damage") or 0)),
    ))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    sys.path.insert(0, str(args.source_root.resolve()))

    from auto_ptu.data_models import MoveSpec
    from auto_ptu.rules import calculations

    original_offense = calculations.offensive_stat
    original_defense = calculations.defensive_stat
    original_exact = calculations.has_ability_exact
    results: list[tuple[str, str]] = []
    try:
        calculations.has_ability_exact = lambda _pokemon, _name: False
        for scenario in SCENARIOS:
            calculations.offensive_stat = lambda _pokemon, _category, ignore_positive_stage=False, value=scenario.attack: value
            calculations.defensive_stat = lambda _pokemon, _category, ignore_positive_stage=False, value=scenario.defense: value

            attacker = FixturePokemon(types=("Normal",), sniper=scenario.sniper)
            defender = FixturePokemon(types=scenario.defender_types)
            move = MoveSpec(
                name="Damage Fixture",
                type=scenario.move_type,
                category="Physical",
                db=scenario.db,
                ac=2,
                range_kind="Ranged",
                target_kind="Ranged",
            )
            context = calculations.AttackContext(attacker=attacker, defender=defender, move=move)
            context.modifiers.extend(modifier(kind, value, calculations) for kind, value in scenario.modifiers)
            result = calculations.resolve_move_action(
                random.Random(scenario.seed),
                attacker,
                defender,
                move,
                context=context,
                accuracy_override={"hit": True, "crit": scenario.crit, "roll": 10, "needed": 2},
            )
            results.append((scenario.name, encode(result)))
    finally:
        calculations.offensive_stat = original_offense
        calculations.defensive_stat = original_defense
        calculations.has_ability_exact = original_exact

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("".join(f"{name}\t{value}\n" for name, value in results), encoding="utf-8")
    print(f"wrote {len(results)} Python damage fixtures to {args.output.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
