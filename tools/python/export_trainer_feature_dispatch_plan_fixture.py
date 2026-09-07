#!/usr/bin/env python3
"""Freeze TrainerFeatureDispatcher traversal behavior from the pinned Python oracle."""
from __future__ import annotations

import argparse
import json
import sys
from collections import OrderedDict
from pathlib import Path
from types import SimpleNamespace


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.rules.trainer_features import TrainerFeatureDispatcher, _feature_identifier

    class SpyDispatcher(TrainerFeatureDispatcher):
        def _feature_prerequisites_met(self, **kwargs):
            return True

        def _feature_matches_context(self, **kwargs):
            return True

        def _feature_is_available(self, trainer, feature):
            return True

        def _feature_has_resources(self, trainer, feature):
            return True

        def _apply_feature(self, *, trainer_id, trainer, feature, actor_id, payload):
            self.applied.append({
                "trainer_id": trainer_id,
                "feature_id": _feature_identifier(feature),
                "runtime_kind": feature.get("runtime_kind", ""),
            })
            return False

    def trainer(features=None, edges=None, known=None):
        return SimpleNamespace(
            features=list(features or []),
            edges=list(edges or []),
            trainer_class={"known_features": list(known or [])},
        )

    battle = SimpleNamespace(trainers=OrderedDict([
        ("trainer-b", trainer(
            features=[
                {"feature_id": "alpha", "name": "Alpha", "trigger": " ROUND_START "},
                {"feature_id": "duplicate", "trigger": "round_start"},
            ],
            edges=[
                {"feature_id": "duplicate", "trigger": "round_start"},
                {"feature_id": "edge-hit", "trigger": "round_start"},
            ],
            known=[{"feature_id": "known-hit", "trigger": "round_start"}],
        )),
        ("trainer-a", trainer(features=[
            {"feature_id": "disabled", "trigger": "round_start", "enabled": False},
            {"feature_id": "wrong", "trigger": "turn_start"},
            {"feature_id": "Named Feature", "trigger": "ROUND_START"},
        ])),
    ]))
    dispatcher = SpyDispatcher(battle)
    dispatcher.applied = []
    dispatcher.trigger(" Round_Start ")

    fixture = {"trigger": " Round_Start ", "invocations": dispatcher.applied}
    expected = [
        {"trainer_id": "trainer-b", "feature_id": "alpha", "runtime_kind": "feature"},
        {"trainer_id": "trainer-b", "feature_id": "duplicate", "runtime_kind": "feature"},
        {"trainer_id": "trainer-b", "feature_id": "edge-hit", "runtime_kind": "edge"},
        {"trainer_id": "trainer-b", "feature_id": "known-hit", "runtime_kind": "feature"},
        {"trainer_id": "trainer-a", "feature_id": "named-feature", "runtime_kind": "feature"},
    ]
    if fixture["invocations"] != expected:
        raise AssertionError(f"unexpected Trainer Feature traversal: {fixture['invocations']!r}")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(fixture, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
