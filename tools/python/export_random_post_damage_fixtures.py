#!/usr/bin/env python3
"""Export RNG-consuming post-result ability behavior from the pinned Python oracle."""

from __future__ import annotations

import argparse
import random
import sys
from dataclasses import dataclass
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.data_models import MoveSpec
    from auto_ptu.rules.hooks.ability_hooks import AbilityHookContext
    from auto_ptu.rules.hooks.abilities.attacker_damage_bonuses import (
        _adaptability_errata_bonus,
        _damp_errata_bonus,
    )

    @dataclass
    class Spec:
        types: list[str]

    class Mon:
        def __init__(self, *, ability, types, temporary_effects=None):
            self.ability = ability
            self.spec = Spec(list(types))
            self.hp = 30
            self._temporary_effects = [dict(entry) for entry in (temporary_effects or [])]

        def ability_names(self):
            return [self.ability] if self.ability else []

        def get_temporary_effects(self, name):
            target = str(name).strip().lower()
            return [
                dict(entry)
                for entry in self._temporary_effects
                if str(entry.get("name", "")).strip().lower() == target
            ]

        def remove_temporary_effect(self, name):
            target = str(name).strip().lower()
            self._temporary_effects = [
                entry
                for entry in self._temporary_effects
                if str(entry.get("name", "")).strip().lower() != target
            ]

    class Battle:
        def __init__(self, seed, round_number):
            self.rng = random.Random(seed)
            self.round = round_number

    cases = [
        ("adaptability_stab", "Adaptability [Errata]", "Fire", ["Fire"], 7, 0, "none"),
        ("adaptability_wrong_type", "Adaptability [Errata]", "Fire", ["Water"], 7, 0, "none"),
        ("damp_water", "Damp [Errata]", "Water", ["Normal"], 19, 0, "none"),
        ("damp_wrong_type", "Damp [Errata]", "Fire", ["Normal"], 19, 0, "none"),
        ("adaptability_inverted", "Adaptability [Errata]", "Fire", ["Fire"], 11, 0, "matching"),
        ("adaptability_expired_break", "Adaptability [Errata]", "Fire", ["Fire"], 13, 2, "expired"),
    ]

    rows = [
        "name\tability\tmove_type\tactor_types\tseed\tround\taura_break_mode\tbonus\tevents\tnext_roll\tremaining_aura_break"
    ]
    for name, ability, move_type, actor_types, seed, round_number, mode in cases:
        effects = []
        if mode == "matching":
            effects.append({
                "name": "aura_break_errata",
                "ability": ability,
                "source_id": "breaker",
                "expires_round": round_number,
            })
        elif mode == "expired":
            effects.append({
                "name": "aura_break_errata",
                "ability": ability,
                "source_id": "breaker",
                "expires_round": round_number - 1,
            })

        attacker = Mon(ability=ability, types=actor_types, temporary_effects=effects)
        defender = Mon(ability="", types=["Normal"])
        battle = Battle(seed, round_number)
        move = MoveSpec(name="Oracle Move", type=move_type, category="Special", db=6, ac=2)
        result = {"hit": True, "damage": 20}
        events = []
        ctx = AbilityHookContext(
            battle=battle,
            attacker_id="actor",
            attacker=attacker,
            defender_id="target",
            defender=defender,
            move=move,
            effective_move=move,
            events=events,
            phase="post_result",
            result=result,
        )
        if ability == "Adaptability [Errata]":
            _adaptability_errata_bonus(ctx)
        elif ability == "Damp [Errata]":
            _damp_errata_bonus(ctx)
        else:
            raise AssertionError(f"unsupported fixture ability: {ability}")

        bonus = int(result.get("damage", 0)) - 20
        signatures = []
        for event in events:
            signatures.append(":".join([
                str(event.get("ability", "")),
                str(event.get("effect", "")),
                str(event.get("amount", "")),
                str(event.get("actor", "")),
                str(event.get("target", "")),
            ]))
        next_roll = battle.rng.randint(1, 10)
        remaining = len(attacker.get_temporary_effects("aura_break_errata"))
        rows.append("\t".join(map(str, [
            name,
            ability,
            move_type,
            "|".join(actor_types),
            seed,
            round_number,
            mode,
            bonus,
            "|".join(signatures),
            next_roll,
            remaining,
        ])))

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
