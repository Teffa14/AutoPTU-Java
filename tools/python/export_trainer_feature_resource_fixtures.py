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

    def render(resources):
        return ";".join(f"{key}={resources[key]}" for key in sorted(resources))

    def evaluate(name: str, feature, resources):
        trainer = SimpleNamespace(feature_resources=dict(resources))
        dispatcher = TrainerFeatureDispatcher(SimpleNamespace())
        available = dispatcher._feature_has_resources(trainer, dict(feature))
        dispatcher._consume_resources(trainer, dict(feature))
        return name, int(bool(available)), render(trainer.feature_resources)

    cases = [
        evaluate("no_cost", {}, {"focus": 3}),
        evaluate("empty_cost", {"resource_cost": {}}, {"focus": 3}),
        evaluate("non_dict_cost", {"resource_cost": ["focus"]}, {"focus": 3}),
        evaluate("exact_balance", {"resource_cost": {"focus": 3}}, {"focus": 3}),
        evaluate("surplus_balance", {"resource_cost": {"focus": 2}}, {"focus": 5}),
        evaluate("insufficient_clamps_on_direct_consume", {"resource_cost": {"focus": 4}}, {"focus": 1}),
        evaluate("missing_resource", {"resource_cost": {"focus": 1}}, {}),
        evaluate("zero_cost_ignored", {"resource_cost": {"focus": 0}}, {"focus": 3}),
        evaluate("negative_cost_ignored", {"resource_cost": {"focus": -2}}, {"focus": 3}),
        evaluate("numeric_string_cost", {"resource_cost": {"focus": "2"}}, {"focus": 4}),
        evaluate("float_string_cost_uses_int_like", {"resource_cost": {"focus": "2.9"}}, {"focus": 4}),
        evaluate("invalid_cost_ignored", {"resource_cost": {"focus": "bogus"}}, {"focus": 4}),
        evaluate("float_balance_uses_direct_int", {"resource_cost": {"focus": 2}}, {"focus": 2.9}),
        evaluate("empty_balance_is_zero", {"resource_cost": {"focus": 1}}, {"focus": ""}),
        evaluate("multiple_resources", {"resource_cost": {"focus": 2, "momentum": 1}}, {"focus": 5, "momentum": 2, "other": 9}),
        evaluate("resource_names_are_case_sensitive", {"resource_cost": {"Focus": 1}}, {"focus": 5}),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for name, available, after in cases:
            handle.write(f"{name}\t{available}\t{after}\n")


if __name__ == "__main__":
    main()
