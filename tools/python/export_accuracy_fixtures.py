#!/usr/bin/env python3
"""Export invariant d20 accuracy behavior from the pinned Python AutoPTU oracle."""
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path
from types import SimpleNamespace


class SequenceRng:
    def __init__(self, *values: int):
        self.values = list(values)

    def randint(self, low: int, high: int) -> int:
        if not self.values:
            raise RuntimeError("accuracy fixture RNG exhausted")
        value = int(self.values.pop(0))
        if not low <= value <= high:
            raise RuntimeError(f"fixture RNG value {value} outside [{low}, {high}]")
        return value


class FixturePokemon:
    def __init__(
        self,
        *,
        abilities=(),
        accuracy_stage: int = 0,
        accuracy_cs: int = 0,
        probability_control: bool = False,
    ):
        self._abilities = {str(value).strip().lower() for value in abilities}
        self.combat_stages = {"accuracy": accuracy_stage}
        self.spec = SimpleNamespace(accuracy_cs=accuracy_cs)
        self._probability_control = probability_control
        self.position = None
        self.battle = None

    def has_ability(self, name: str) -> bool:
        return str(name or "").strip().lower() in self._abilities

    def get_temporary_effects(self, name: str):
        if name == "probability_control" and self._probability_control:
            return [{}]
        return []

    def remove_temporary_effect(self, name: str):
        if name == "probability_control":
            self._probability_control = False

    def has_status(self, _name: str) -> bool:
        return False

    def is_trainer_combatant(self) -> bool:
        return False

    def has_trainer_feature(self, _name: str) -> bool:
        return False


@dataclass(frozen=True)
class Scenario:
    name: str
    ac: int | None
    evasion: int
    combat_stage: int
    accuracy_cs: int
    accuracy_bonus: int
    roll: int
    reroll: int | None = None
    crit_range: int = 20
    target_kind: str = "Ranged"
    attacker_abilities: tuple[str, ...] = ()
    defender_abilities: tuple[str, ...] = ()


SCENARIOS = (
    Scenario("natural_one_standard", 2, 0, 0, 0, 0, 1),
    Scenario("minimum_needed_roll_two", 2, 0, 0, 0, 0, 2),
    Scenario("natural_twenty_high_needed", 25, 0, 0, 0, 0, 20),
    Scenario("high_needed_roll_nineteen", 25, 0, 0, 0, 0, 19),
    Scenario("stage_clamp_positive", 10, 3, 9, 0, 0, 7),
    Scenario("stage_clamp_negative", 2, 0, -9, 0, 0, 8),
    Scenario(
        "no_guard_melee",
        6,
        5,
        0,
        0,
        0,
        6,
        target_kind="Melee",
        attacker_abilities=("No Guard",),
    ),
    Scenario(
        "no_guard_ranged_does_not_remove_evasion",
        6,
        5,
        0,
        0,
        0,
        6,
        target_kind="Ranged",
        attacker_abilities=("No Guard",),
    ),
    Scenario("ac_none_automatic_natural_one", None, 9, 0, 0, 0, 1),
    Scenario("ac_none_automatic_crit18", None, 9, 0, 0, 0, 18, crit_range=18),
    Scenario(
        "blur_ac_none",
        None,
        7,
        0,
        0,
        0,
        5,
        defender_abilities=("Blur",),
    ),
    Scenario(
        "blur_natural_one",
        None,
        7,
        0,
        0,
        0,
        1,
        defender_abilities=("Blur",),
    ),
    Scenario("reroll_natural_one_to_hit", 10, 0, 0, 0, 0, 1, reroll=15),
    Scenario("reroll_miss_to_crit", 10, 0, 0, 0, 0, 5, reroll=20),
    Scenario("accuracy_components", 10, 3, 2, 1, 2, 8),
    Scenario("crit18_hit", 10, 0, 0, 0, 0, 18, crit_range=18),
    Scenario("crit_threshold_on_miss", 20, 0, 0, 0, 0, 18, crit_range=18),
)


def encode(result: dict, probability: float) -> str:
    return ",".join(
        (
            "true" if bool(result.get("hit")) else "false",
            "true" if bool(result.get("crit")) else "false",
            str(int(result.get("roll") or 0)),
            str(int(result.get("needed") or 0)),
            f"{float(probability):.6f}",
        )
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    sys.path.insert(0, str(args.source_root.resolve()))
    from auto_ptu.data_models import MoveSpec
    from auto_ptu.rules import calculations

    original_evasion = calculations.evasion_value_for_attack
    original_accuracy_bonus = calculations._temporary_accuracy_bonus
    results: list[tuple[str, str]] = []

    try:
        for scenario in SCENARIOS:
            calculations.evasion_value_for_attack = (
                lambda _attacker, _defender, _category, value=scenario.evasion: value
            )
            calculations._temporary_accuracy_bonus = (
                lambda _attacker, _defender, _move, value=scenario.accuracy_bonus: value
            )

            attacker = FixturePokemon(
                abilities=scenario.attacker_abilities,
                accuracy_stage=scenario.combat_stage,
                accuracy_cs=scenario.accuracy_cs,
                probability_control=scenario.reroll is not None,
            )
            defender = FixturePokemon(abilities=scenario.defender_abilities)
            move = MoveSpec(
                name="Accuracy Fixture",
                type="Normal",
                category="Physical",
                db=5,
                ac=scenario.ac,
                range_kind=scenario.target_kind,
                target_kind=scenario.target_kind,
                crit_range=scenario.crit_range,
            )
            rng_values = [scenario.roll]
            if scenario.reroll is not None:
                rng_values.append(scenario.reroll)
            result = calculations.attack_hits(SequenceRng(*rng_values), attacker, defender, move)

            # hit_probability does not consume Probability Control, so a fresh actor
            # keeps the scenario independent from attack_hits mutation.
            probability_attacker = FixturePokemon(
                abilities=scenario.attacker_abilities,
                accuracy_stage=scenario.combat_stage,
                accuracy_cs=scenario.accuracy_cs,
            )
            probability_defender = FixturePokemon(abilities=scenario.defender_abilities)
            probability = calculations.hit_probability(
                probability_attacker,
                probability_defender,
                move,
            )
            results.append((scenario.name, encode(result, probability)))
    finally:
        calculations.evasion_value_for_attack = original_evasion
        calculations._temporary_accuracy_bonus = original_accuracy_bonus

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "".join(f"{name}\t{value}\n" for name, value in results),
        encoding="utf-8",
    )
    print(f"wrote {len(results)} Python accuracy oracle fixtures to {args.output.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
