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

        def _apply_status(self, _events, **_kwargs):
            return None

        def _apply_combat_stage(self, _events, **kwargs):
            target_id = str(kwargs.get("target_id") or "")
            role = "user" if target_id == "actor" else "target"
            self.applied.append((role, str(kwargs.get("stat") or ""), int(kwargs.get("delta") or 0)))

    def run(name: str, text: str, roll: int):
        battle = FakeBattle()
        attacker = FakeMon()
        defender = FakeMon()
        move = SimpleNamespace(
            name="Oracle Generic Combat Stage",
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
            move_name="oracle generic combat stage",
            hit=True,
            phase="post_damage",
            action_type="Standard",
        )
        move_specials._generic_post_damage_from_text(ctx)
        encoded = ";".join(f"{role}|{stat}|{delta}" for role, stat, delta in battle.applied)
        return name, encoded

    cases = [
        ("raise_target_threshold_hit", "Raises the target's Attack by +2 Combat Stage on 18+.", 18),
        ("raise_target_threshold_miss", "Raises the target's Attack by +2 Combat Stage on 18+.", 17),
        ("lower_target", "Lowers the target's Defense by -1 CS.", 1),
        ("raise_user_multi", "Raises the user's Special Attack / Speed by +1 Combat Stage.", 1),
        ("lower_user_multi", "Lowers the user's Special Defense and Accuracy by -2 Combat Stage.", 1),
        ("alt_target_lower", "Target's Evasion is lowered by -2 Combat Stages.", 1),
        ("alt_raise", "Raises the user's Accuracy 1 Combat Stage.", 1),
        ("simple_all_targets_lower", "All legal targets have their Speed lowered by -1 Combat Stage.", 1),
        ("nbsp_normalization", "Raises\u00a0the target's Attack by +1 Combat Stage.", 1),
        ("dedupe_stats", "Raises the user's Attack / Attack by +1 Combat Stage.", 1),
    ]

    rows = ["name\texpected"]
    rows.extend("\t".join(run(*case)) for case in cases)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
