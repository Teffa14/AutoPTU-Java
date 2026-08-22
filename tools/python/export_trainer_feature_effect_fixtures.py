#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from collections import OrderedDict
from pathlib import Path


class Mon:
    def __init__(
        self,
        controller_id: str,
        hp: int,
        max_hp: int,
        *,
        active: bool = True,
        fainted: bool = False,
        stages=None,
        temp_hp: int = 0,
        statuses=None,
        temporary_effects=None,
    ):
        self.controller_id = controller_id
        self.hp = hp
        self.max_hp = max_hp
        self.active = active
        self.fainted = fainted
        self.statuses = list(statuses or [])
        self.combat_stages = dict(stages or {})
        self.temp_hp = int(temp_hp or 0)
        self.temporary_effects = list(temporary_effects or [])

    def heal(self, amount):
        self.hp = min(self.max_hp, int(self.hp or 0) + int(amount or 0))

    def has_status(self, name):
        wanted = str(name).strip().lower()
        return any(str(status).strip().lower() == wanted for status in self.statuses)

    def get_temporary_effects(self, name):
        wanted = str(name).strip().lower()
        return [effect for effect in self.temporary_effects if str(effect.get("name", "")).strip().lower() == wanted]

    def add_temp_hp(self, amount: int) -> int:
        amount = max(0, int(amount))
        if amount <= 0:
            return 0
        if self.has_status("Heal Blocked") or self.has_status("Heal Block"):
            return 0
        if self.get_temporary_effects("temp_hp_locked"):
            return 0
        self.temp_hp += amount
        return amount


def run_case(
    dispatcher_cls,
    *,
    effect,
    feature=None,
    actor_id="ally",
    payload=None,
    hp_by_id=None,
    stages_by_id=None,
    temp_hp_by_id=None,
    statuses_by_id=None,
    locked_ids=None,
):
    hp_by_id = hp_by_id or {"ally": (5, 20), "ally_full": (20, 20), "enemy": (4, 20)}
    stages_by_id = stages_by_id or {}
    temp_hp_by_id = temp_hp_by_id or {}
    statuses_by_id = statuses_by_id or {}
    locked_ids = set(locked_ids or [])
    pokemon = OrderedDict(
        (
            pid,
            Mon(
                "t1" if pid.startswith("ally") else "t2",
                hp,
                max_hp,
                stages=stages_by_id.get(pid, {}),
                temp_hp=temp_hp_by_id.get(pid, 0),
                statuses=statuses_by_id.get(pid, []),
                temporary_effects=[{"name": "temp_hp_locked"}] if pid in locked_ids else [],
            ),
        )
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
    stage_snapshot = ";".join(
        f"{pid}:atk={int(mon.combat_stages.get('atk', 0) or 0)},def={int(mon.combat_stages.get('def', 0) or 0)},"
        f"spatk={int(mon.combat_stages.get('spatk', 0) or 0)},spdef={int(mon.combat_stages.get('spdef', 0) or 0)},"
        f"spd={int(mon.combat_stages.get('spd', 0) or 0)},accuracy={int(mon.combat_stages.get('accuracy', 0) or 0)}"
        for pid, mon in pokemon.items()
    )
    temp_hp_snapshot = ",".join(f"{pid}={mon.temp_hp}" for pid, mon in pokemon.items())
    return int(bool(applied)), str(effect_type), ",".join(targets), str(amount), hp_snapshot, stage_snapshot, temp_hp_snapshot


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))
    from auto_ptu.rules.trainer_features import TrainerFeatureDispatcher

    cases = OrderedDict()
    cases["heal_injured_actor"] = run_case(TrainerFeatureDispatcher, effect={"type": "heal", "amount": 7, "target_rules": {"scope": "actor"}})
    cases["heal_active_alias"] = run_case(TrainerFeatureDispatcher, effect={"type": "heal_active", "amount": 3, "target_rules": {"scope": "actor"}})
    cases["heal_full_hp_not_applied"] = run_case(TrainerFeatureDispatcher, effect={"type": "heal", "amount": 7, "target_rules": {"scope": "actor"}}, actor_id="ally_full")
    cases["heal_zero_not_applied"] = run_case(TrainerFeatureDispatcher, effect={"type": "heal", "amount": 0, "target_rules": {"scope": "actor"}})
    cases["heal_negative_not_applied"] = run_case(TrainerFeatureDispatcher, effect={"type": "heal", "amount": -3, "target_rules": {"scope": "actor"}})
    cases["heal_float_string_int_like"] = run_case(TrainerFeatureDispatcher, effect={"type": "heal", "amount": "4.9", "target_rules": {"scope": "actor"}})
    cases["heal_multiple_only_changed_targets"] = run_case(TrainerFeatureDispatcher, effect={"type": "heal", "amount": 5, "target_rules": {"scope": "all_allies"}})
    cases["effect_target_rules_override_feature"] = run_case(
        TrainerFeatureDispatcher,
        feature={"target_rules": {"scope": "active_allies"}},
        effect={"type": "heal", "amount": 4, "target_rules": {"scope": "active_enemies"}},
    )
    cases["blank_effect_is_log_only"] = run_case(TrainerFeatureDispatcher, effect={})
    cases["unknown_effect_is_applied_scaffold"] = run_case(TrainerFeatureDispatcher, effect={"type": "future_effect"})

    cases["grant_temp_hp_actor"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "grant_temp_hp", "amount": 5, "target_rules": {"scope": "actor"}},
    )
    cases["grant_temp_hp_stacks_without_cap"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "grant_temp_hp", "amount": 25, "target_rules": {"scope": "actor"}},
        temp_hp_by_id={"ally": 4},
    )
    cases["grant_temp_hp_heal_blocked"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "grant_temp_hp", "amount": 5, "target_rules": {"scope": "actor"}},
        statuses_by_id={"ally": ["Heal Blocked"]},
    )
    cases["grant_temp_hp_heal_block_alias"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "grant_temp_hp", "amount": 5, "target_rules": {"scope": "actor"}},
        statuses_by_id={"ally": ["Heal Block"]},
    )
    cases["grant_temp_hp_locked"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "grant_temp_hp", "amount": 5, "target_rules": {"scope": "actor"}},
        locked_ids={"ally"},
    )
    cases["grant_temp_hp_zero_not_applied"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "grant_temp_hp", "amount": 0, "target_rules": {"scope": "actor"}},
    )
    cases["grant_temp_hp_float_string_int_like"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "grant_temp_hp", "amount": "4.9", "target_rules": {"scope": "actor"}},
    )
    cases["grant_temp_hp_multiple_allies"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "grant_temp_hp", "amount": 3, "target_rules": {"scope": "all_allies"}},
    )

    cases["raise_cs_single_attack"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "raise_cs", "stat": "attack", "amount": 2, "target_rules": {"scope": "actor"}},
    )
    cases["raise_cs_multiple_stats_and_accuracy"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "raise_cs", "stats": {"atk": 2, "defense": -1, "accuracy": 1}, "target_rules": {"scope": "actor"}},
    )
    cases["raise_cs_alias_special_attack"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "raise_cs", "stat": "special-attack", "amount": "3.9", "target_rules": {"scope": "actor"}},
    )
    cases["raise_cs_clamps_upper"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "raise_cs", "stat": "atk", "amount": 4, "target_rules": {"scope": "actor"}},
        stages_by_id={"ally": {"atk": 5}},
    )
    cases["raise_cs_clamps_lower"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "raise_cs", "stat": "def", "amount": -4, "target_rules": {"scope": "actor"}},
        stages_by_id={"ally": {"def": -5}},
    )
    cases["raise_cs_accuracy_clamps"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "raise_cs", "stat": "acc", "amount": 9, "target_rules": {"scope": "actor"}},
        stages_by_id={"ally": {"accuracy": 5}},
    )
    cases["raise_cs_invalid_stat_not_applied"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "raise_cs", "stat": "hp", "amount": 2, "target_rules": {"scope": "actor"}},
    )
    cases["raise_cs_zero_not_applied"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "raise_cs", "stat": "atk", "amount": 0, "target_rules": {"scope": "actor"}},
    )

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for name, result in cases.items():
            handle.write(name + "\t" + "\t".join(map(str, result)) + "\n")


if __name__ == "__main__":
    main()
