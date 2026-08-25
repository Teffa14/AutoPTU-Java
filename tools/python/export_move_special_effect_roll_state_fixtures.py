#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
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

    class Mon:
        def __init__(self, effects=()):
            self.temporary_effects = [dict(x) for x in effects]
            self.combat_stages = {}
        def get_temporary_effects(self, kind):
            return [x for x in self.temporary_effects if x.get("kind") == kind]
        def has_ability(self, _): return False
        def has_trainer_feature(self, _): return False
        def hardened_crit_effect_bonus(self, _): return 0

    class Battle:
        round = 3
        def abilities_suppressed_for(self, _): return False
        def _roll_penalty(self, _): return 0

    def run(name, attacker_effects=(), defender_effects=(), move_name="Test"):
        attacker = Mon(attacker_effects)
        defender = Mon(defender_effects)
        move = SimpleNamespace(name=move_name, type="Normal", category="Physical", effects_text="", target_kind="Melee", range_kind="Melee", range_text="Melee")
        ctx = SimpleNamespace(attacker_id="actor", attacker=attacker, defender=defender, battle=Battle(), move=move, result={"roll": 10})
        roll = move_specials._effect_roll(ctx)
        def encode(entries):
            return json.dumps(entries, separators=(",", ":"), sort_keys=True)
        return name, roll, encode(attacker.temporary_effects), encode(defender.temporary_effects)

    rows = [
        run("expired_immutable_then_clear", defender_effects=({"kind":"immutable_mind_block","move":"Test","expires_round":2},)),
        run("immutable_other_move_survives", defender_effects=({"kind":"immutable_mind_block","move":"Other","expires_round":5},)),
        run("expired_range_block_then_live", attacker_effects=(
            {"kind":"effect_range_block","expires_round":2},
            {"kind":"effect_range_block","expires_round":5},
            {"kind":"effect_range_bonus","amount":9,"expires_round":2},
        )),
        run("expired_and_live_bonus", attacker_effects=(
            {"kind":"effect_range_bonus","amount":4,"expires_round":2},
            {"kind":"effect_range_bonus","amount":"3","expires_round":5},
            {"kind":"effect_range_bonus","amount":"bad","expires_round":5},
        )),
        run("immutable_short_circuits_attacker_cleanup",
            attacker_effects=({"kind":"effect_range_bonus","amount":4,"expires_round":2},),
            defender_effects=({"kind":"immutable_mind_block","move":"Test","expires_round":5},)),
    ]
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("".join("\t".join(map(str, row)) + "\n" for row in rows), encoding="utf-8")


if __name__ == "__main__":
    main()
