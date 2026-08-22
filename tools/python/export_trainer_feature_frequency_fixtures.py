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

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.rules.trainer_features import TrainerFeatureDispatcher

    def evaluate(name: str, feature, *, usage=None, round_number=3):
        feature = dict(feature)
        feature.setdefault("feature_id", "frequency-probe")
        trainer = SimpleNamespace(feature_usage={"frequency-probe": dict(usage or {})})
        battle = SimpleNamespace(round=round_number)
        dispatcher = TrainerFeatureDispatcher(battle)
        total_limit, round_limit = dispatcher._frequency_limits(feature)
        available = dispatcher._feature_is_available(trainer, feature)
        return name, total_limit, round_limit, int(bool(available))

    cases = [
        evaluate("baseline_at_will", {}),
        evaluate("daily_default", {"frequency": "Daily"}),
        evaluate("scene_default", {"frequency": " scene "}),
        evaluate("encounter_default", {"frequency": "ENCOUNTER"}),
        evaluate("eot_default", {"frequency": "EOT"}),
        evaluate("round_default", {"frequency": "Round"}),
        evaluate("turn_default", {"frequency": "Turn"}),
        evaluate("x_round_default", {"frequency": "x/round"}),
        evaluate("per_round_dash_default", {"frequency": "per-round"}),
        evaluate("per_round_space_default", {"frequency": "per round"}),
        evaluate("two_per_round", {"frequency": "2/round"}),
        evaluate("three_per_turn", {"frequency": "3 / turn"}),
        evaluate("four_per_scene", {"frequency": "4/scene"}),
        evaluate("five_per_daily", {"frequency": "5 / daily"}),
        evaluate("six_per_encounter", {"frequency": "6/encounter"}),
        evaluate("explicit_total_precedence", {"frequency": "Daily", "max_uses": 3}),
        evaluate("explicit_round_precedence", {"frequency": "EOT", "uses_per_round": 4}),
        evaluate("explicit_both", {"frequency": "2/round", "max_uses": 7, "uses_per_round": 5}),
        evaluate("negative_daily_defaults", {"frequency": "Daily", "max_uses": -2}),
        evaluate("negative_round_defaults", {"frequency": "Round", "uses_per_round": -4}),
        evaluate("zero_per_scene", {"frequency": "0/scene"}),
        evaluate("unknown_frequency", {"frequency": "Special"}),
        evaluate("numeric_string_limits", {"max_uses": "3", "uses_per_round": "2"}),
        evaluate("cooldown_before_blocks", {}, usage={"cooldown_until": 4}, round_number=3),
        evaluate("cooldown_equal_blocks", {}, usage={"cooldown_until": 3}, round_number=3),
        evaluate("cooldown_after_allows", {}, usage={"cooldown_until": 2}, round_number=3),
        evaluate("total_below_allows", {"frequency": "Daily", "max_uses": 2}, usage={"uses_total": 1}),
        evaluate("total_equal_blocks", {"frequency": "Daily", "max_uses": 2}, usage={"uses_total": 2}),
        evaluate("total_above_blocks", {"frequency": "Daily", "max_uses": 2}, usage={"uses_total": 3}),
        evaluate("round_below_allows", {"frequency": "2/round"}, usage={"uses_round_3": 1}),
        evaluate("round_equal_blocks", {"frequency": "2/round"}, usage={"uses_round_3": 2}),
        evaluate("other_round_ignored", {"frequency": "2/round"}, usage={"uses_round_2": 99}),
        evaluate("cooldown_precedes_usage", {"frequency": "Daily"}, usage={"cooldown_until": 3, "uses_total": 99}),
        evaluate("string_usage_coercion", {"frequency": "2/round", "max_uses": 3}, usage={"uses_total": "2", "uses_round_3": "1"}),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for name, total_limit, round_limit, available in cases:
            handle.write(f"{name}\t{total_limit}\t{round_limit}\t{available}\n")


if __name__ == "__main__":
    main()
