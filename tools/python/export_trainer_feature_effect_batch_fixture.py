#!/usr/bin/env python3
"""Freeze TrainerFeatureDispatcher effect extraction and aggregation from the pinned oracle."""
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

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.rules.trainer_features import TrainerFeatureDispatcher

    class Battle:
        def __init__(self):
            self.events = []

        def log_event(self, event):
            self.events.append(dict(event))

    class SpyDispatcher(TrainerFeatureDispatcher):
        def _apply_effect(self, *, effect, **kwargs):
            effect_type = str(effect.get("type") or "").strip().lower()
            if effect_type == "skip":
                return False, "skip", ["ignored"], {"ignored": True}
            if effect_type == "first":
                return True, "first", ["a", "b"], {"order": 1}
            if effect_type == "second":
                return True, "second", ["b", "c"], {"order": 2}
            if not effect_type:
                return True, "log_only", [], {}
            return False, effect_type, [], {}

    battle = Battle()
    dispatcher = SpyDispatcher(battle)
    trainer = SimpleNamespace()

    feature = {
        "feature_id": "batch-probe",
        "name": "Batch Probe",
        "trigger": "round_start",
        "effects": [
            {"type": "skip"},
            "ignored-non-dict",
            {"type": "first"},
            {"type": "second"},
        ],
        "effect_payload": {"type": "must-not-win"},
    }
    extracted = dispatcher._feature_effects(feature)
    applied = dispatcher._apply_feature(
        trainer_id="trainer-1",
        trainer=trainer,
        feature=feature,
        actor_id=None,
        payload={"round": 3},
    )
    if not applied or len(battle.events) != 1:
        raise AssertionError("expected one applied Trainer Feature semantic event")
    event = battle.events[0]

    empty_fallback = dispatcher._feature_effects({"effects": ["noise"]})
    payload_fallback = dispatcher._feature_effects({"effect_payload": [{"type": "first"}, "noise"]})
    primary_fallback = dispatcher._feature_effects({"effect": {"type": "second"}})

    rows = {
        "extracted_types": "|".join(str(entry.get("type") or "") for entry in extracted),
        "empty_fallback_log_only": int(empty_fallback == [{}]),
        "payload_fallback_types": "|".join(str(entry.get("type") or "") for entry in payload_fallback),
        "primary_fallback_types": "|".join(str(entry.get("type") or "") for entry in primary_fallback),
        "event_effect_type": str(event.get("effect_type") or ""),
        "event_effect_types": "|".join(event.get("effect_types") or []),
        "event_targets": "|".join(event.get("targets") or []),
        "event_detail_orders": "|".join(str(item.get("order")) for item in (event.get("details") or [])),
        "event_actor_defaults_to_trainer": int(event.get("actor") == "trainer-1"),
    }
    expected = {
        "extracted_types": "skip|first|second",
        "empty_fallback_log_only": 1,
        "payload_fallback_types": "first",
        "primary_fallback_types": "second",
        "event_effect_type": "multi",
        "event_effect_types": "first|second",
        "event_targets": "a|b|c",
        "event_detail_orders": "1|2",
        "event_actor_defaults_to_trainer": 1,
    }
    if rows != expected:
        raise AssertionError(f"unexpected Trainer Feature effect batch fixture: {rows!r}")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{key}\t{value}" for key, value in rows.items()) + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
