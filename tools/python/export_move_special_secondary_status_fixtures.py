#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from types import SimpleNamespace


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    root = Path(args.source_root).resolve()
    sys.path.insert(0, str(root))
    from auto_ptu.rules.hooks import move_specials

    class FakeMon:
        def __init__(self):
            self.temporary_effects = []
            self.combat_stages = {}

        def has_ability(self, _name):
            return False

        def has_trainer_feature(self, _name):
            return False

        def get_temporary_effects(self, kind):
            return [entry for entry in self.temporary_effects if entry.get("kind") == kind]

        def hardened_crit_effect_bonus(self, _battle):
            return 0

    class FakeBattle:
        def __init__(self):
            self.round = 3
            self.applied = []

        def abilities_suppressed_for(self, _actor_id):
            return False

        def _roll_penalty(self, _attacker):
            return 0

        def _apply_status(self, _events, **kwargs):
            self.applied.append((
                str(kwargs.get("status") or ""),
                str(kwargs.get("effect") or ""),
                kwargs.get("remaining"),
            ))

    def run(name: str, text: str, roll: int):
        battle = FakeBattle()
        attacker = FakeMon()
        defender = FakeMon()
        move = SimpleNamespace(
            name="Oracle Generic Status",
            type="Normal",
            category="Physical",
            effects_text=text,
            target_kind="Melee",
            range_kind="Melee",
            range_text="Melee",
        )
        ctx = move_specials.MoveSpecialContext(
            battle=battle,
            attacker_id="actor",
            attacker=attacker,
            defender_id="defender",
            defender=defender,
            move=move,
            result={"hit": True, "roll": roll},
            damage_dealt=1,
            events=[],
            move_name="oracle generic status",
            hit=True,
            phase="post_damage",
            action_type="Standard",
        )
        move_specials._generic_post_damage_from_text(ctx)
        encoded = ";".join(
            f"{status}|{effect}|{'' if remaining is None else remaining}"
            for status, effect, remaining in battle.applied
        )
        return name, encoded

    cases = [
        ("threshold_burn_hit", "Burns the target on 18+.", 18),
        ("threshold_burn_miss", "Burns the target on 18+.", 17),
        ("threshold_flinch", "Flinches on a 19+.", 19),
        ("past_tense_paralyzed_quirk", "Paralyzed target on 18+.", 20),
        ("always_poison", "Poisons the target.", 1),
        ("always_freeze", "Freezes the target.", 1),
        ("past_tense_frozen_quirk", "Frozen target.", 1),
        ("even_paralyze_hit", "Paralyzes the target on an even-numbered roll.", 12),
        ("even_paralyze_miss", "Paralyzes the target on an even-numbered roll.", 11),
        ("falls_asleep", "The target falls asleep.", 1),
        ("threshold_then_sleep", "Burns the target on 18+. The target falls asleep.", 18),
        ("nbsp_normalization", "Burns\u00a0the target on 18+.", 18),
    ]

    rows = ["name\texpected"]
    rows.extend("\t".join(run(*case)) for case in cases)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
