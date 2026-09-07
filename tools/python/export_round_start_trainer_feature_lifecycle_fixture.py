#!/usr/bin/env python3
"""Freeze the pinned Python Trainer Feature round-start execution boundary."""

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

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.rules.trainer_features import TrainerFeatureDispatcher

    alpha = {
        "feature_id": "alpha",
        "name": "Alpha",
        "trigger": "round_start",
        "resource_cost": {"focus": 1},
    }
    beta = {
        "feature_id": "beta",
        "name": "Beta",
        "trigger": "round_start",
        "conditions": {"min_round": 4},
    }

    trainer_b = SimpleNamespace(
        features=[alpha],
        edges=[],
        trainer_class={},
        feature_resources={"focus": 2},
        feature_usage={},
    )
    trainer_a = SimpleNamespace(
        features=[beta],
        edges=[],
        trainer_class={},
        feature_resources={},
        feature_usage={},
    )

    class Battle:
        def __init__(self) -> None:
            self.round = 3
            self.trainers = {"trainer-b": trainer_b, "trainer-a": trainer_a}
            self.pokemon = {}
            self.events = []

        def log_event(self, event: dict) -> None:
            self.events.append(dict(event))

    battle = Battle()
    TrainerFeatureDispatcher(battle).trigger("round_start", payload={"round": battle.round})

    events = [event for event in battle.events if event.get("type") == "trainer_feature"]
    if len(events) != 1:
        raise AssertionError(f"expected one applied Trainer Feature event, got {events!r}")
    event = events[0]
    alpha_usage = trainer_b.feature_usage.get("alpha", {})

    values = {
        "event_count": str(len(events)),
        "event_trainer": str(event.get("trainer", "")),
        "event_actor": str(event.get("actor", "")),
        "event_feature_id": str(event.get("feature_id", "")),
        "event_feature": str(event.get("feature", "")),
        "event_feature_kind": str(event.get("feature_kind", "")),
        "event_trigger": str(event.get("trigger", "")),
        "event_effect_type": str(event.get("effect_type", "")),
        "event_effect_types": "|".join(str(value) for value in event.get("effect_types", [])),
        "event_targets": "|".join(str(value) for value in event.get("targets", [])),
        "event_payload_round": str((event.get("payload") or {}).get("round", "")),
        "resource_focus": str(trainer_b.feature_resources.get("focus", "")),
        "alpha_uses_total": str(alpha_usage.get("uses_total", "")),
        "alpha_last_round": str(alpha_usage.get("last_round", "")),
        "alpha_uses_round": str(alpha_usage.get("uses_round_3", "")),
        "beta_usage_count": str(len(trainer_a.feature_usage)),
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("".join(f"{key}\t{value}\n" for key, value in values.items()), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
