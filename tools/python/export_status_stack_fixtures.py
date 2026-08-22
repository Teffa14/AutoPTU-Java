#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from collections import OrderedDict
from pathlib import Path


class Mon:
    def __init__(self, statuses=None):
        self.controller_id = "t1"
        self.active = True
        self.fainted = False
        self.statuses = list(statuses or [])

    def _normalized_status_name(self, status):
        if isinstance(status, dict):
            raw = status.get("name", "")
        else:
            raw = status
        return str(raw or "").strip().lower()

    def has_status(self, name):
        wanted = str(name or "").strip().lower()
        return any(self._normalized_status_name(status) == wanted for status in self.statuses)

    def _upgrade_status_entry(self, status, *, canonical_name):
        if isinstance(status, dict):
            return status
        for index, existing in enumerate(self.statuses):
            if existing is status or existing == status:
                entry = {"name": canonical_name}
                self.statuses[index] = entry
                return entry
        raise RuntimeError("status entry disappeared during upgrade")

    def remove_status_by_names(self, names):
        wanted = {str(name or "").strip().lower() for name in names}
        for index, status in enumerate(self.statuses):
            normalized = self._normalized_status_name(status)
            if normalized in wanted:
                self.statuses.pop(index)
                return normalized
        return None


def normalize_status(status):
    if isinstance(status, dict):
        name = str(status.get("name", "")).strip().lower()
        source = str(status.get("source", ""))
        remaining = status.get("remaining", "")
        duration = status.get("duration", "")
        return f"{name}|{source}|{remaining}|{duration}"
    return f"{str(status).strip().lower()}|||"


def run_case(dispatcher_cls, *, effect, statuses):
    mon = Mon(statuses)
    battle = type("Battle", (), {"pokemon": OrderedDict([("ally", mon)]), "trainers": {"t1": object()}})()
    dispatcher = dispatcher_cls(battle)
    applied, effect_type, targets, detail = dispatcher._apply_effect(
        trainer_id="t1",
        trainer=battle.trainers["t1"],
        feature={"name": "Stack Test"},
        effect=dict(effect),
        actor_id="ally",
        payload={},
    )
    snapshot = ";".join(normalize_status(status) for status in mon.statuses)
    detail_status = str(detail.get("status", "")) if isinstance(detail, dict) else ""
    detail_duration = str(detail.get("duration", "")) if isinstance(detail, dict) else ""
    removed = ",".join(map(str, detail.get("removed", []))) if isinstance(detail, dict) else ""
    return int(bool(applied)), str(effect_type), ",".join(targets), snapshot, detail_status, detail_duration, removed


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))
    from auto_ptu.rules.trainer_features import TrainerFeatureDispatcher

    cases = OrderedDict()
    cases["apply_new_duration"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "apply_status", "status": "Poisoned", "duration": 3, "target_rules": {"scope": "actor"}},
        statuses=[],
    )
    cases["refresh_first_shorter_duration"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "apply_status", "status": "Poisoned", "duration": 5, "target_rules": {"scope": "actor"}},
        statuses=[{"name": "Poisoned", "source": "move:a", "remaining": 2, "duration": 2}, {"name": "Poisoned", "source": "move:b", "remaining": 1, "duration": 1}],
    )
    cases["existing_longer_no_change"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "apply_status", "status": "Poisoned", "duration": 5, "target_rules": {"scope": "actor"}},
        statuses=[{"name": "Poisoned", "source": "move:a", "remaining": 7, "duration": 7}],
    )
    cases["stack_appends_duplicate"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "apply_status", "status": "Poisoned", "duration": 4, "stack": True, "target_rules": {"scope": "actor"}},
        statuses=[{"name": "Poisoned", "source": "move:a", "remaining": 2, "duration": 2}, "Burned"],
    )
    cases["zero_duration_existing_no_change"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "apply_status", "status": "Poisoned", "target_rules": {"scope": "actor"}},
        statuses=[{"name": "Poisoned", "source": "move:a", "remaining": 2, "duration": 2}],
    )
    cases["remove_named_removes_all_duplicates"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "remove_status", "status": "Poisoned", "target_rules": {"scope": "actor"}},
        statuses=[{"name": "Poisoned", "source": "move:a"}, "Burned", {"name": "POISONED", "source": "move:b"}],
    )
    cases["remove_all_clears_every_entry"] = run_case(
        TrainerFeatureDispatcher,
        effect={"type": "remove_status", "all": True, "target_rules": {"scope": "actor"}},
        statuses=[{"name": "Poisoned", "source": "move:a"}, "Burned", {"name": "Poisoned", "source": "move:b"}],
    )

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for name, result in cases.items():
            handle.write(name + "\t" + "\t".join(map(str, result)) + "\n")


if __name__ == "__main__":
    main()
