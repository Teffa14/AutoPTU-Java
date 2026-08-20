#!/usr/bin/env python3
"""Export Analytic post-result behavior from the pinned Python AutoPTU oracle."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from types import SimpleNamespace


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.data_models import MoveSpec
    from auto_ptu.rules.hooks.ability_hooks import AbilityHookContext
    from auto_ptu.rules.hooks.abilities.attacker_damage_bonuses import _analytic_bonus

    class Attacker:
        hp = 30

        def get_temporary_effects(self, name):
            return []

        def remove_temporary_effect(self, name):
            return None

    class Defender:
        def __init__(self, actions_taken):
            self.hp = 30
            self.actions_taken = list(actions_taken)

    class Battle:
        def __init__(self, initiative_index, defender_index):
            self.round = 0
            self._initiative_index = initiative_index
            self.initiative_order = []
            if defender_index >= 0:
                for idx in range(defender_index + 1):
                    actor_id = "target" if idx == defender_index else f"other-{idx}"
                    self.initiative_order.append(SimpleNamespace(actor_id=actor_id))

    cases = [
        ("actions_taken", "Physical", 1, -1, -1),
        ("not_acted_no_cursor", "Physical", 0, -1, 0),
        ("cursor_before_target", "Physical", 0, 0, 1),
        ("cursor_on_target", "Physical", 0, 1, 1),
        ("cursor_past_target", "Physical", 0, 2, 1),
        ("target_missing_from_order", "Physical", 0, 4, -1),
        ("status_never_applies_with_actions", "Status", 1, 4, 1),
        ("status_never_applies_with_cursor", "Status", 0, 4, 1),
    ]

    rows = ["name\tcategory\tactions_taken\tinitiative_index\tdefender_index\texpected_bonus\texpected_events"]
    for name, category, action_count, initiative_index, defender_index in cases:
        attacker = Attacker()
        defender = Defender(["used"] * action_count)
        battle = Battle(initiative_index, defender_index)
        move = MoveSpec(name="Oracle Move", type="Normal", category=category, db=6, ac=2)
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
        _analytic_bonus(ctx)
        bonus = int(result.get("damage", 0)) - 20
        rows.append("\t".join(map(str, [
            name,
            category,
            action_count,
            initiative_index,
            defender_index,
            bonus,
            len(events),
        ])))

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
