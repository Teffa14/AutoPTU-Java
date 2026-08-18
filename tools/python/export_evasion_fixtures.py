#!/usr/bin/env python3
"""Export PTU evasion observations from the pinned Python calculations oracle."""
from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path
from types import SimpleNamespace


class FakePokemon:
    def __init__(
        self,
        *,
        defense=10,
        spdef=10,
        spd=10,
        evasion_phys=0,
        evasion_spec=0,
        evasion_spd=0,
        speed_stage=0,
        statuses=(),
        abilities=(),
    ):
        self.spec = SimpleNamespace(
            defense=defense,
            spdef=spdef,
            spd=spd,
            evasion_phys=evasion_phys,
            evasion_spec=evasion_spec,
            evasion_spd=evasion_spd,
            level=1,
            items=[],
            trainer_features=[],
        )
        self.combat_stages = {"spd": speed_stage}
        self._statuses = {str(value).strip().lower() for value in statuses}
        self._abilities = [str(value).strip() for value in abilities]
        self.battle = None

    def has_status(self, name):
        return str(name).strip().lower() in self._statuses

    def has_ability(self, name):
        target = str(name).strip().lower()
        return any(value.lower().split(" [", 1)[0] == target for value in self._abilities)

    def ability_names(self):
        return list(self._abilities)

    def get_temporary_effects(self, _name):
        return []

    def has_trainer_feature(self, _name):
        return False

    def has_capability(self, _name):
        return False

    def hardened_evasion_bonus(self, _battle):
        return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))
    from auto_ptu.rules.calculations import evasion_value

    cases = [
        ("physical_base", FakePokemon(defense=17, evasion_phys=2), "Physical", False),
        ("special_base", FakePokemon(spdef=24, evasion_spec=-1), "Special", False),
        ("status_stage", FakePokemon(spd=19, evasion_spd=1, speed_stage=2), "Status", False),
        ("status_paralyzed", FakePokemon(spd=19, evasion_spd=1, speed_stage=2, statuses=("Paralyzed",)), "Status", False),
        ("sleep_suppresses_positive", FakePokemon(defense=17, evasion_phys=3, statuses=("Sleep",)), "Physical", False),
        ("sleep_keeps_negative", FakePokemon(defense=17, evasion_phys=-2, statuses=("Asleep",)), "Physical", False),
        ("heavy_metal_physical", FakePokemon(defense=19, abilities=("Heavy Metal [Errata]",)), "Physical", False),
        ("light_metal_physical", FakePokemon(defense=21, abilities=("Light Metal [Errata]",)), "Physical", False),
        ("heavy_metal_status", FakePokemon(spd=20, abilities=("Heavy Metal [Errata]",)), "Status", False),
        ("light_metal_status", FakePokemon(spd=19, abilities=("Light Metal [Errata]",)), "Status", False),
        ("keen_eye_style_ignore", FakePokemon(defense=17, evasion_phys=5), "Physical", True),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, delimiter="\t")
        for case_id, pokemon, category, ignore_non_stat in cases:
            writer.writerow([case_id, str(evasion_value(pokemon, category, ignore_non_stat=ignore_non_stat))])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
