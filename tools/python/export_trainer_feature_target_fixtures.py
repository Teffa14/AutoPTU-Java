#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from collections import OrderedDict
from pathlib import Path


class Mon:
    def __init__(self, controller_id: str, *, active: bool, fainted: bool, statuses=()):
        self.controller_id = controller_id
        self.active = active
        self.fainted = fainted
        self._statuses = {str(value).strip().lower() for value in statuses}

    def has_status(self, name):
        return str(name or "").strip().lower() in self._statuses


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.rules.trainer_features import TrainerFeatureDispatcher

    pokemon = OrderedDict(
        [
            ("ally_active", Mon("t1", active=True, fainted=False, statuses=("Burned",))),
            ("ally_inactive", Mon("t1", active=False, fainted=False, statuses=("Sleep",))),
            ("ally_fainted", Mon("t1", active=True, fainted=True, statuses=("Poisoned",))),
            ("enemy_active", Mon("t2", active=True, fainted=False, statuses=("Burned", "Poisoned"))),
            ("enemy_inactive", Mon("t2", active=False, fainted=False)),
        ]
    )
    battle = type("Battle", (), {"pokemon": pokemon})()
    dispatcher = TrainerFeatureDispatcher(battle)

    cases = [
        ("default_active_allies", {}, "ally_active", {}),
        ("actor", {"scope": "actor"}, "ally_active", {}),
        ("actor_missing", {"scope": "self"}, "missing", {}),
        ("target", {"scope": "target"}, "ally_active", {"target_id": "enemy_active"}),
        ("target_missing", {"scope": "action_target"}, "ally_active", {"target_id": "missing"}),
        ("targets_preserve_payload_order", {"scope": "targets"}, "ally_active", {"target_ids": ["enemy_active", "missing", "ally_active", "enemy_active"]}),
        ("targets_string_is_not_sequence", {"scope": "action_targets"}, "ally_active", {"target_ids": "enemy_active"}),
        ("all_active_filters_fainted_later", {"scope": "all_active"}, "ally_active", {}),
        ("all_allies_includes_inactive_by_default", {"scope": "all_allies"}, "ally_active", {}),
        ("allies_explicit_exclude_inactive", {"scope": "allies", "include_inactive": False}, "ally_active", {}),
        ("active_allies", {"scope": "self_team"}, "ally_active", {}),
        ("all_enemies_includes_inactive", {"scope": "foes"}, "ally_active", {}),
        ("active_enemies", {"scope": "foe_active"}, "ally_active", {}),
        ("all_pokemon_default_inactive", {"scope": "all_pokemon"}, "ally_active", {}),
        ("all_include_fainted", {"scope": "all", "include_fainted": True}, "ally_active", {}),
        ("required_status_any", {"scope": "all", "require_status": ["Sleep", "Poisoned"]}, "ally_active", {}),
        ("excluded_status_any", {"scope": "all", "exclude_status": ["Burned", "Sleep"]}, "ally_active", {}),
        ("required_and_excluded", {"scope": "all", "require_status": "Poisoned", "exclude_status": "Burned"}, "ally_active", {}),
        ("limit_preserves_order", {"scope": "all", "include_fainted": True, "limit": 2}, "ally_active", {}),
        ("float_string_limit_int_like", {"scope": "all", "include_fainted": True, "limit": "2.9"}, "ally_active", {}),
        ("unknown_scope_safe_default", {"scope": "mystery"}, "ally_active", {}),
        ("target_alias_precedence", {"target": "all_enemies"}, "ally_active", {}),
        ("scope_precedes_target", {"scope": "active_allies", "target": "all_enemies"}, "ally_active", {}),
        ("bool_like_include_fainted", {"scope": "all", "include_fainted": "yes"}, "ally_active", {}),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for name, rules, actor_id, payload in cases:
            targets = dispatcher._targets_for_feature(
                trainer_id="t1",
                actor_id=actor_id,
                payload=dict(payload),
                rules=dict(rules),
            )
            handle.write(f"{name}\t{','.join(targets)}\n")


if __name__ == "__main__":
    main()
