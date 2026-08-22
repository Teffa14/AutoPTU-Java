#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from collections import OrderedDict
from pathlib import Path


def run_case(dispatcher_cls, effect):
    trainers = OrderedDict((trainer_id, object()) for trainer_id in ("t1", "t2", "t3"))
    battle = type("Battle", (), {"trainers": trainers})()
    dispatcher = dispatcher_cls(battle)
    targets = dispatcher._resolve_trainer_targets("t1", dict(effect))
    return ",".join(targets)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))
    from auto_ptu.rules.trainer_features import TrainerFeatureDispatcher

    cases = OrderedDict()
    cases["default_self"] = {}
    cases["self"] = {"trainer_scope": "self"}
    cases["ally_alias"] = {"trainer_scope": "ally"}
    cases["allies_alias"] = {"trainer_scope": "allies"}
    cases["self_team_alias"] = {"trainer_scope": "self_team"}
    cases["own_alias"] = {"trainer_scope": "own"}
    cases["enemy"] = {"trainer_scope": "enemy"}
    cases["foe_alias"] = {"trainer_scope": "foe"}
    cases["opponent_alias"] = {"trainer_scope": "opponent"}
    cases["all"] = {"trainer_scope": "all"}
    cases["any_alias"] = {"trainer_scope": "any"}
    cases["explicit_other_trainer"] = {"trainer_scope": "t3"}
    cases["explicit_source_trainer"] = {"trainer_scope": "t1"}
    cases["unknown_falls_back_self"] = {"trainer_scope": "missing"}
    cases["trainer_field_fallback"] = {"trainer": "t2"}
    cases["trainer_scope_precedes_trainer"] = {"trainer_scope": "t3", "trainer": "t2"}
    cases["blank_scope_uses_trainer"] = {"trainer_scope": "", "trainer": "t2"}
    cases["false_scope_uses_trainer"] = {"trainer_scope": False, "trainer": "t2"}
    cases["zero_scope_uses_trainer"] = {"trainer_scope": 0, "trainer": "t2"}
    cases["blank_both_defaults_self"] = {"trainer_scope": "", "trainer": ""}
    cases["normalizes_case_and_space"] = {"trainer_scope": "  T3  "}

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for name, effect in cases.items():
            handle.write(f"{name}\t{run_case(TrainerFeatureDispatcher, effect)}\n")


if __name__ == "__main__":
    main()
