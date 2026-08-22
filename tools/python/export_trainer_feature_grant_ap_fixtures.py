#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from collections import OrderedDict
from pathlib import Path


class Trainer:
    def __init__(self, ap: int):
        self.ap = ap


def run_case(dispatcher_cls, *, effect, source="t1"):
    trainers = OrderedDict((tid, Trainer(ap)) for tid, ap in (("t1", 5), ("t2", 2), ("t3", 0)))
    battle = type("Battle", (), {"trainers": trainers, "pokemon": {}})()
    dispatcher = dispatcher_cls(battle)
    applied, effect_type, targets, detail = dispatcher._apply_effect(
        trainer_id=source,
        trainer=trainers[source],
        feature={},
        effect=dict(effect),
        actor_id=None,
        payload={},
    )
    amount = detail.get("amount", "") if isinstance(detail, dict) else ""
    changed = detail.get("trainers", []) if isinstance(detail, dict) else []
    ap_snapshot = ",".join(f"{tid}={trainer.ap}" for tid, trainer in trainers.items())
    return int(bool(applied)), str(effect_type), ",".join(targets), str(amount), ",".join(changed), ap_snapshot


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))
    from auto_ptu.rules.trainer_features import TrainerFeatureDispatcher

    cases = OrderedDict()
    cases["default_amount_self"] = run_case(TrainerFeatureDispatcher, effect={"type": "grant_ap"})
    cases["explicit_amount_self"] = run_case(TrainerFeatureDispatcher, effect={"type": "grant_ap", "amount": 3})
    cases["zero_not_applied"] = run_case(TrainerFeatureDispatcher, effect={"type": "grant_ap", "amount": 0})
    cases["negative_not_applied"] = run_case(TrainerFeatureDispatcher, effect={"type": "grant_ap", "amount": -2})
    cases["float_string_int_like"] = run_case(TrainerFeatureDispatcher, effect={"type": "grant_ap", "amount": "4.9"})
    cases["ally_alias_is_self"] = run_case(TrainerFeatureDispatcher, effect={"type": "grant_ap", "amount": 2, "trainer_scope": "ally"})
    cases["enemy_targets_other_trainers"] = run_case(TrainerFeatureDispatcher, effect={"type": "grant_ap", "amount": 2, "trainer_scope": "enemy"})
    cases["all_targets_in_order"] = run_case(TrainerFeatureDispatcher, effect={"type": "grant_ap", "amount": 1, "trainer_scope": "all"})
    cases["explicit_trainer_id"] = run_case(TrainerFeatureDispatcher, effect={"type": "grant_ap", "amount": 5, "trainer": "t2"})
    cases["unknown_selector_falls_back_self"] = run_case(TrainerFeatureDispatcher, effect={"type": "grant_ap", "amount": 2, "trainer": "missing"})
    cases["trainer_scope_precedes_trainer"] = run_case(TrainerFeatureDispatcher, effect={"type": "grant_ap", "amount": 2, "trainer_scope": "enemy", "trainer": "t1"})
    cases["false_scope_falls_back_trainer"] = run_case(TrainerFeatureDispatcher, effect={"type": "grant_ap", "amount": 2, "trainer_scope": False, "trainer": "t2"})

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for name, result in cases.items():
            handle.write(name + "\t" + "\t".join(map(str, result)) + "\n")


if __name__ == "__main__":
    main()
