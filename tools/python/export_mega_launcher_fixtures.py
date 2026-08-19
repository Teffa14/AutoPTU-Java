#!/usr/bin/env python3
"""Export pinned Python Mega Launcher effective-move DB behavior for Java parity tests."""
from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path


class FakePokemon:
    def __init__(self, abilities=(), hp=20):
        self._abilities = {str(value).strip().lower() for value in abilities}
        self.hp = hp

    def has_ability(self, name):
        return str(name or "").strip().lower() in self._abilities


class FakeBattle:
    def abilities_suppressed_for(self, _combatant_id):
        return False


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.data_models import MoveSpec
    from auto_ptu.rules.hooks.ability_hooks import AbilityHookContext, apply_ability_hooks

    cases = [
        ("normal_water_pulse", "Water Pulse", 8, "Mega Launcher"),
        ("errata_water_pulse", "Water Pulse", 8, "Mega Launcher [Errata]"),
        ("normal_cap", "Aura Sphere", 19, "Mega Launcher"),
        ("errata_cap", "Dark Pulse", 19, "Mega Launcher [Errata]"),
        ("non_pulse", "Hydro Pump", 8, "Mega Launcher"),
        ("no_ability", "Dragon Pulse", 8, ""),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(["name", "move_name", "base_db", "ability", "effective_db", "event_amount", "effect_events"])
        for name, move_name, base_db, ability in cases:
            effective_db, event_amount, effect_events = evaluate_case(
                MoveSpec, AbilityHookContext, apply_ability_hooks, move_name, base_db, ability
            )
            writer.writerow([name, move_name, base_db, ability, effective_db, event_amount, effect_events])


def evaluate_case(MoveSpec, AbilityHookContext, apply_ability_hooks, move_name, base_db, ability):
    attacker = FakePokemon([ability] if ability else [])
    defender = FakePokemon()
    move = MoveSpec(name=move_name, type="Water", category="Special", db=base_db, ac=2, freq="EOT")
    effective = MoveSpec(**move.__dict__)
    events = []
    ctx = AbilityHookContext(
        battle=FakeBattle(),
        attacker_id="actor",
        attacker=attacker,
        defender_id="target",
        defender=defender,
        move=move,
        effective_move=effective,
        events=events,
        phase="pre_damage",
    )
    apply_ability_hooks("pre_damage", ctx)
    matching = [
        event for event in events
        if event.get("type") == "ability"
        and event.get("effect") == "db_bonus"
        and str(event.get("ability") or "") == ability
    ]
    amount = sum(int(event.get("amount", 0) or 0) for event in matching)
    return int(ctx.effective_move.db or 0), amount, len(matching)


if __name__ == "__main__":
    main()
