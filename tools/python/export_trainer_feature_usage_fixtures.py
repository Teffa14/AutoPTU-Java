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

    def render(usage):
        chunks = []
        for feature_id in sorted(usage):
            info = usage[feature_id]
            inner = ",".join(f"{key}={info[key]}" for key in sorted(info))
            chunks.append(f"{feature_id}[{inner}]")
        return ";".join(chunks)

    def evaluate(name: str, feature, usage, current_round: int, actor_id=None):
        trainer = SimpleNamespace(feature_usage={key: dict(value) for key, value in usage.items()})
        dispatcher = TrainerFeatureDispatcher(SimpleNamespace(round=current_round))
        dispatcher._mark_feature_use(trainer, dict(feature), actor_id=actor_id)
        return name, render(trainer.feature_usage)

    cases = [
        evaluate("new_usage_from_name", {"name": "Quick Draw"}, {}, 4),
        evaluate(
            "increments_existing_usage",
            {"feature_id": "steady-hand"},
            {"steady-hand": {"uses_total": 2, "uses_round_4": 1, "legacy": 7}},
            4,
        ),
        evaluate(
            "tracks_actor_round",
            {"feature_id": "steady-hand"},
            {"steady-hand": {"uses_total": 1}},
            4,
            actor_id="mon-1",
        ),
        evaluate(
            "empty_actor_does_not_track",
            {"feature_id": "steady-hand"},
            {"steady-hand": {"uses_total": 1}},
            4,
            actor_id="",
        ),
        evaluate("cooldown_rounds", {"feature_id": "burst", "cooldown_rounds": 2}, {}, 4),
        evaluate("cooldown_fallback", {"feature_id": "burst", "cooldown": "3.9"}, {}, 4),
        evaluate(
            "cooldown_rounds_none_overrides_fallback",
            {"feature_id": "burst", "cooldown_rounds": None, "cooldown": 5},
            {},
            4,
        ),
        evaluate(
            "nonpositive_cooldown_preserves_existing",
            {"feature_id": "burst", "cooldown_rounds": 0},
            {"burst": {"cooldown_until": 9}},
            4,
        ),
        evaluate(
            "cooldown_overwrites_existing",
            {"feature_id": "burst", "cooldown_rounds": 2},
            {"burst": {"cooldown_until": 99}},
            4,
        ),
        evaluate(
            "feature_id_has_priority",
            {"feature_id": "primary", "id": "secondary", "name": "Display Name"},
            {},
            2,
        ),
        evaluate("id_fallback", {"id": "Second Choice", "name": "Display Name"}, {}, 2),
        evaluate("default_feature_id", {}, {}, 2),
        evaluate(
            "numeric_usage_values",
            {"feature_id": "numbers"},
            {"numbers": {"uses_total": "2", "uses_round_5": 2.9}},
            5,
        ),
        evaluate(
            "falsey_usage_values",
            {"feature_id": "zeros"},
            {"zeros": {"uses_total": "", "uses_round_0": 0}},
            0,
        ),
        evaluate(
            "preserves_other_features",
            {"feature_id": "used"},
            {"other": {"uses_total": 8}, "used": {"uses_total": 1}},
            3,
        ),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for name, rendered in cases:
            handle.write(f"{name}\t{rendered}\n")


if __name__ == "__main__":
    main()
