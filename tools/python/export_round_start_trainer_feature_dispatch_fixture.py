#!/usr/bin/env python3
"""Freeze the pinned Python round-start Trainer Feature dispatcher call and ordering."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from export_round_start_event_fixture import BattleDouble


class DispatcherSpy:
    def __init__(self, timeline):
        self.timeline = timeline
        self.calls = []

    def trigger(self, event_name, *args, **kwargs):
        call = {"event_name": event_name, "args": list(args), "kwargs": dict(kwargs)}
        self.calls.append(call)
        self.timeline.append("trainer_feature_dispatch")
        return []


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    sys.path.insert(0, str(args.source_root.resolve()))
    from auto_ptu.rules.controllers.phase_controller import PhaseController

    battle = BattleDouble()
    timeline = []
    dispatcher = DispatcherSpy(timeline)
    battle.trainer_feature_dispatcher = dispatcher

    original_log_event = battle.log_event

    def log_event(event):
        if event.get("type") == "round_start":
            timeline.append("round_start_event")
        elif event.get("type") == "ability" and event.get("ability") == "Air Lock":
            timeline.append("air_lock")
        original_log_event(event)

    battle.log_event = log_event
    battle._active_ability_holders = lambda name: ["alpha"] if name == "Air Lock" else []

    PhaseController(battle).start_round()

    if len(dispatcher.calls) != 1:
        raise AssertionError(f"expected one Trainer Feature trigger, got {len(dispatcher.calls)}")
    call = dispatcher.calls[0]
    if call["args"]:
        raise AssertionError(f"round_start trigger unexpectedly used positional args: {call['args']}")
    if set(call["kwargs"]) != {"payload"}:
        raise AssertionError(f"round_start trigger kwargs changed: {call['kwargs']}")
    payload = call["kwargs"]["payload"]
    if payload != {"round": battle.round}:
        raise AssertionError(f"round_start payload changed: {payload}")
    expected_timeline = ["round_start_event", "trainer_feature_dispatch", "air_lock"]
    if timeline != expected_timeline:
        raise AssertionError(f"round_start ordering changed: {timeline}")

    lines = [
        f"TRIGGER\t{call['event_name']}",
        f"ROUND\t{payload['round']}",
        "ACTOR_ARGUMENT\tabsent",
        "TARGET_ARGUMENT\tabsent",
        "TIMELINE\t" + ",".join(timeline),
    ]
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(output.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
