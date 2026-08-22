#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from types import SimpleNamespace


def _serialize_selected(dispatcher, trainer) -> str:
    rows = []
    for feature in dispatcher._trainer_features(trainer):
        fid = str(feature.get("feature_id") or feature.get("id") or feature.get("name") or "").strip().lower().replace(" ", "-") or "feature"
        rows.append(f"{fid}:{feature.get('runtime_kind', '')}")
    return "|".join(rows)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.rules.trainer_features import TrainerFeatureDispatcher

    battle = SimpleNamespace(trainers={}, pokemon={}, round=3, phase="START")
    dispatcher = TrainerFeatureDispatcher(battle)

    trainer = SimpleNamespace(
        features=["Quick Switch", {"feature_id": "alpha", "name": "Ignored Name"}],
        edges=["Quick Switch", {"id": "edge-two", "name": "Ignored Edge Name"}],
        trainer_class={
            "class_id": "Ace Trainer",
            "subclass_id": "Commander",
            "level": "4.9",
            "known_features": ["Class Gift", {"name": "alpha"}],
        },
        feature_usage={},
    )
    selection = _serialize_selected(dispatcher, trainer)

    cases = []

    def check(name: str, feature: dict, *, trainer_override=None) -> None:
        current = trainer_override or trainer
        result = dispatcher._feature_prerequisites_met(
            trainer_id="trainer-a",
            trainer=current,
            feature=feature,
        )
        cases.append((name, int(bool(result))))

    check("baseline", {})
    check("min_level_pass", {"min_trainer_level": 4})
    check("min_level_fail", {"min_trainer_level": 5})
    check("level_required_float_string", {"level_required": "4.9"})
    check("required_class_casefold", {"required_classes": [" ACE TRAINER "]})
    check("required_class_fail", {"required_classes": ["researcher"]})
    check("required_subclass_pass", {"required_subclasses": " commander "})
    check("required_subclass_fail", {"required_subclasses": "ranger"})
    check("required_feature_pass", {"required_features": ["alpha", "class-gift"]})
    check("required_feature_space_does_not_hyphenate", {"required_features": ["Class Gift"]})
    check("nested_pass", {"prerequisites": {"level": 4, "class": "ace trainer", "subclass": "commander", "features": "alpha"}})
    check("nested_level_fail", {"prerequisites": {"min_trainer_level": 5}})
    check("nested_feature_fail", {"prerequisites": {"feature": "missing"}})

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        handle.write(f"selection\t{selection}\n")
        for name, result in cases:
            handle.write(f"prerequisite\t{name}\t{result}\n")


if __name__ == "__main__":
    main()
