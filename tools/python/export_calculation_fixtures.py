#!/usr/bin/env python3
"""Export deterministic pure calculation results from pinned Python AutoPTU."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from types import SimpleNamespace


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    sys.path.insert(0, str(args.source_root.resolve()))
    from auto_ptu.data_models import MoveSpec
    from auto_ptu.rules import calculations

    scenarios: list[tuple[str, str]] = []

    for stage in (-99, -6, -2, -1, 0, 1, 2, 6, 99):
        scenarios.append((f"clamp_{stage}", str(calculations.clamp_stage(stage))))
        scenarios.append((f"stage_mult_{stage}", repr(float(calculations.stage_multiplier(stage)))))
        scenarios.append((f"accuracy_stage_{stage}", str(calculations.accuracy_stage_value(stage))))

    weather_cases = [
        ("rain_water", "Rain", "Water"),
        ("storm_electric", " storm ", "ELECTRIC"),
        ("downpour_fire", "Downpour", "Fire"),
        ("sun_fire", "Harsh Sunlight", "Fire"),
        ("sunny_water", "Sunny", "Water"),
        ("hail_ice", "Hail", "Ice"),
        ("sand_rock", "Sandstorm", "Rock"),
        ("rain_grass", "Rain", "Grass"),
    ]
    for name, weather, move_type in weather_cases:
        move = MoveSpec(name="Fixture", type=move_type)
        scenarios.append((name, str(calculations.weather_db_modifier(move, weather))))

    for name, crit_range, hit_chance in [
        ("crit20_full", 20, 1.0),
        ("crit18_full", 18, 1.0),
        ("crit18_lowhit", 18, 0.10),
        ("crit_default_zero", 0, 1.0),
    ]:
        move = MoveSpec(name="Fixture", type="Normal", crit_range=crit_range)
        scenarios.append((name, repr(float(calculations.crit_probability(move, hit_chance)))))

    class FakeAttacker:
        def __init__(self, burned: bool):
            self.burned = burned

        def has_status(self, name):
            return self.burned and name == "Burned"

    for name, category, burned in [
        ("burn_physical_101", "Physical", True),
        ("burn_special_101", "Special", True),
        ("not_burned_physical_101", "Physical", False),
    ]:
        move = MoveSpec(name="Fixture", type="Normal", category=category)
        scenarios.append((
            name,
            str(calculations.apply_status_modifiers(101, FakeAttacker(burned), move)),
        ))

    modifiers = [
        calculations.AttackModifier("half", "damage_scalar", 0.5),
        calculations.AttackModifier("bonus-a", "damage_flat", 5),
        calculations.AttackModifier("third", "damage_scalar", 1.0 / 3.0),
        calculations.AttackModifier("bonus-b", "damage_flat", 2),
    ]
    scenarios.append((
        "flat_then_scalar_floor",
        str(calculations.apply_context_damage_modifiers(100, SimpleNamespace(modifiers=modifiers))),
    ))

    for name, range_kind, target_kind in [
        ("range_melee_first", "Melee, 1 Target", "Ranged"),
        ("range_cone_first", "Cone", "Melee"),
        ("range_target_fallback", "", "Light Melee"),
        ("range_default", "", ""),
    ]:
        move = MoveSpec(
            name="Fixture",
            type="Normal",
            range_kind=range_kind,
            target_kind=target_kind,
        )
        scenarios.append((name, calculations._normalized_range_kind(move)))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(f"{name}\t{value}" for name, value in scenarios) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(scenarios)} Python calculation fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
