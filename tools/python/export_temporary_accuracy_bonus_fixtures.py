#!/usr/bin/env python3
"""Export deterministic fixtures for calculations._temporary_accuracy_bonus()."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from types import SimpleNamespace


class StubPokemon:
    def __init__(
        self,
        *,
        abilities=(),
        temporary_effects=None,
        item_names=(),
        position=None,
        battle=None,
        evasion=0,
    ):
        self._abilities = list(abilities)
        self._temporary_effects = temporary_effects or {}
        self._item_names = {str(name).strip().lower() for name in item_names}
        self.position = position
        self.battle = battle
        self.evasion = evasion

    def ability_names(self):
        return list(self._abilities)

    def has_ability(self, name):
        target = str(name or "").strip().lower()
        return any(str(value or "").strip().lower() == target for value in self._abilities)

    def has_item_named(self, name):
        return str(name or "").strip().lower() in self._item_names

    def get_temporary_effects(self, kind):
        return list(self._temporary_effects.get(kind, []))


def move(*, name="Tackle", category="Physical", type_="Normal"):
    return SimpleNamespace(name=name, category=category, type=type_)


def pokemon(**kwargs):
    return StubPokemon(**kwargs)


def compute(calculations, attacker, defender=None, move_spec=None):
    defender = defender or pokemon()
    move_spec = move_spec or move()
    return calculations._temporary_accuracy_bonus(attacker, defender, move_spec)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    sys.path.insert(0, str(args.source_root))
    from auto_ptu.rules import calculations  # pylint: disable=import-outside-toplevel

    original_evasion_value = calculations.evasion_value
    calculations.evasion_value = lambda mon, category: mon.evasion
    try:
        rows = []

        rows.append(("baseline", compute(calculations, pokemon())))
        rows.append((
            "focused_default",
            compute(calculations, pokemon(temporary_effects={"focused_training": [{}]})),
        ))
        focused_battle = SimpleNamespace(_focused_training_accuracy_bonus=lambda attacker, defender: 3)
        rows.append((
            "focused_helper",
            compute(calculations, pokemon(
                temporary_effects={"focused_training": [{}]}, battle=focused_battle)),
        ))
        rows.append(("compound_eyes", compute(calculations, pokemon(abilities=["Compound Eyes"]))))
        rows.append(("keen_eye", compute(calculations, pokemon(abilities=["Keen Eye"]))))
        rows.append((
            "attacker_no_guard_errata",
            compute(calculations, pokemon(abilities=["No Guard [Errata]"])),
        ))
        rows.append((
            "defender_no_guard_errata",
            compute(calculations, pokemon(), pokemon(abilities=["No Guard [Errata]"])),
        ))
        rows.append((
            "hustle_errata_status",
            compute(calculations, pokemon(abilities=["Hustle [Errata]"]), move_spec=move(category="Status")),
        ))
        rows.append((
            "hustle_base_physical",
            compute(calculations, pokemon(abilities=["Hustle"]), move_spec=move(category="Physical")),
        ))
        rows.append((
            "hustle_base_special",
            compute(calculations, pokemon(abilities=["Hustle"]), move_spec=move(category="Special")),
        ))
        rows.append((
            "hustle_errata_precedence",
            compute(calculations, pokemon(abilities=["Hustle", "Hustle [Errata]"]), move_spec=move(category="Physical")),
        ))
        rows.append((
            "frisk_near",
            compute(
                calculations,
                pokemon(abilities=["Frisk [SuMo Errata]"], position=(0, 0)),
                pokemon(position=(1, 1)),
            ),
        ))
        rows.append((
            "frisk_far",
            compute(
                calculations,
                pokemon(abilities=["Frisk [SuMo Errata]"], position=(0, 0)),
                pokemon(position=(2, 0)),
            ),
        ))
        rows.append((
            "bone_wielder",
            compute(
                calculations,
                pokemon(abilities=["Bone Wielder"], item_names=["Thick Club"]),
                move_spec=move(name="Bonemerang"),
            ),
        ))
        rows.append((
            "shell_cannon",
            compute(
                calculations,
                pokemon(
                    abilities=["Shell Cannon"],
                    temporary_effects={"shell_cannon_ready": [{}]},
                ),
                move_spec=move(name="Hydro Pump"),
            ),
        ))
        rows.append((
            "typed_accuracy_bonus",
            compute(
                calculations,
                pokemon(temporary_effects={"accuracy_bonus": [
                    {"type": "Water", "amount": 2},
                    {"amount": 1},
                    {"type": "Fire", "amount": 5},
                ]}),
                move_spec=move(type_="Water"),
            ),
        ))
        rows.append((
            "lower_av_bonus",
            compute(
                calculations,
                pokemon(
                    evasion=4,
                    temporary_effects={"accuracy_bonus_vs_lower_av": [
                        {"type": "Water", "amount": 2},
                        {"type": "Water", "amount": -1},
                        {"type": "Fire", "amount": 9},
                    ]},
                ),
                pokemon(evasion=2),
                move(type_="Water"),
            ),
        ))
        rows.append((
            "lower_av_not_lower",
            compute(
                calculations,
                pokemon(
                    evasion=2,
                    temporary_effects={"accuracy_bonus_vs_lower_av": [
                        {"type": "Water", "amount": 2},
                    ]},
                ),
                pokemon(evasion=4),
                move(type_="Water"),
            ),
        ))
        chronicler_battle = SimpleNamespace(_chronicler_accuracy_bonus=lambda attacker, defender: 4)
        rows.append((
            "chronicler",
            compute(calculations, pokemon(battle=chronicler_battle)),
        ))

        combined_battle = SimpleNamespace(
            _focused_training_accuracy_bonus=lambda attacker, defender: 1,
            _chronicler_accuracy_bonus=lambda attacker, defender: 4,
        )
        rows.append((
            "combined",
            compute(
                calculations,
                pokemon(
                    abilities=[
                        "Compound Eyes", "Keen Eye", "No Guard [Errata]", "Hustle [Errata]",
                        "Frisk [SuMo Errata]", "Bone Wielder", "Shell Cannon",
                    ],
                    temporary_effects={
                        "focused_training": [{}],
                        "shell_cannon_ready": [{}],
                        "accuracy_bonus": [
                            {"type": "Water", "amount": 2},
                            {"amount": 1},
                        ],
                        "accuracy_bonus_vs_lower_av": [
                            {"type": "Water", "amount": 2},
                        ],
                    },
                    item_names=["Thick Club"],
                    position=(0, 0),
                    battle=combined_battle,
                    evasion=4,
                ),
                pokemon(
                    abilities=["No Guard [Errata]"],
                    position=(1, 0),
                    evasion=2,
                ),
                move(name="Bone Rush", category="Physical", type_="Water"),
            ),
        ))
    finally:
        calculations.evasion_value = original_evasion_value

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "case\texpected\n" + "".join(f"{name}\t{expected}\n" for name, expected in rows),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
