#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from collections import OrderedDict
from pathlib import Path


class Mon:
    def __init__(self, controller_id: str, hp: int, max_hp: int, *, active: bool = True, fainted: bool = False):
        self.controller_id = controller_id
        self.hp = hp
        self.max_hp = max_hp
        self.active = active
        self.fainted = fainted
        self.statuses = []

    def heal(self, amount):
        self.hp = min(self.max_hp, int(self.hp or 0) + int(amount or 0))

    def has_status(self, name):
        return False


def run_case(dispatcher_cls, *, effect, feature=None, actor_id="ally", payload=None, hp_by_id=None):
    hp_by_id = hp_by_id or {"ally": (5, 20), "ally_full": (20, 20), "enemy": (4, 20)}
    pokemon = OrderedDict(
        (pid, Mon("t1" if pid.startswith("ally") else "t2", hp, max_hp))
        for pid, (hp, max_hp) in hp_by_id.items()
    )
    battle = type("Battle", (), {"pokemon": pokemon, "trainers": {"t1": object(), "t2": object()}})()
    dispatcher = dispatcher_cls(battle)
    applied, effect_type, targets, detail = dispatcher._apply_effect(
        trainer_id="t1",
        trainer=battle.trainers["t1"],
        feature=dict(feature or {}),
        effect=dict(effect),
        actor_id=actor_id,
        payload=dict(payload or {}),
    )
    hp_snapshot = ",".join(f"{pid}={mon.hp}" for pid, mon in pokemon.items())
    amount = detail.get("amount", "") if isinstance(detail, dict) else ""
    return int(bool(applied)), str(effect_type), ",".join(targets), str(amount), hp_snapshot


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))
    from auto_ptu.rules.trainer_features import TrainerFeatureDispatcher

    cases = OrderedDict()
    cases["heal_injured_actor"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "heal", "amount": 7, "target_rules": {"scope": "actor"}},
    )
    cases["heal_active_alias"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "heal_active", "amount": 3, "target_rules": {"scope": "actor"}},
    )
    cases["heal_full_hp_not_applied"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "heal", "amount": 7, "target_rules": {"scope": "actor"}},
        actor_id="ally_full",
    )
    cases["heal_zero_not_applied"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "heal", "amount": 0, "target_rules": {"scope": "actor"}},
    )
    cases["heal_negative_not_applied"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "heal", "amount": -3, "target_rules": {"scope": "actor"}},
    )
    cases["heal_float_string_int_like"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "heal", "amount": "4.9", "target_rules": {"scope": "actor"}},
    )
    cases["heal_multiple_only_changed_targets"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "heal", "amount": 5, "target_rules": {"scope": "all_allies"}},
    )
    cases["effect_target_rules_override_feature"] = run_case(
        TrainerFeatureDispatcher,
        feature={"target_rules": {"scope": "active_allies"}},
        effect={"type": "heal", "amount": 4, "target_rules": {"scope": "active_enemies"}},
    )
    cases["blank_effect_is_log_only"] = run_case(TrainerFeatureDispatcher, effect={})
    cases["unknown_effect_is_applied_scaffold"] = run_case(
        TrainerFeatureDispatcher, effect={"type": "future_effect"}
    )

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for name, result in cases.items():
            handle.write(name + "\t" + "\t".join(map(str, result)) + "\n")


if __name__ == "__main__":
    main()
