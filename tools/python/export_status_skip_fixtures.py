#!/usr/bin/env python3
"""Export base status-skip action transitions from the pinned Python AutoPTU oracle."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from types import SimpleNamespace


class FixtureActor:
    def __init__(self, actions_taken=None):
        self.actions_taken = dict(actions_taken or {})
        self.spec = SimpleNamespace(poke_edge_choices={})
        self.controller_id = "trainer"
        self.hp = 10

    def mark_action(self, action_type, detail):
        self.actions_taken[action_type] = detail

    def get_temporary_effects(self, _slug):
        return []

    def add_temporary_effect(self, *_args, **_kwargs):
        return None

    def handle_phase_effects(self, *_args, **_kwargs):
        return []


class FixtureBattle:
    def __init__(self, actor, pending):
        self._pending_status_skip = dict(pending)
        self.current_actor_id = "actor"
        self.pokemon = {"actor": actor}
        self.round = 1
        self.phase = SimpleNamespace(value="start")
        self.events = []

    def log_event(self, payload):
        self.events.append(dict(payload))


def run_case(status_controller, actions_taken, *, status, reason):
    action_type = status_controller.ActionType
    actor = FixtureActor(actions_taken)
    pending = {"status": status, "phase": "start", "effect": reason}
    battle = FixtureBattle(actor, pending)
    skipped = status_controller.StatusController(battle).consume_pending_status_skip()
    event = battle.events[-1]
    standard = actor.actions_taken.get(action_type.STANDARD, "-")
    shift = actor.actions_taken.get(action_type.SHIFT, "-")
    stable = "|".join(
        [
            str(event.get("type") or ""),
            str(event.get("actor") or ""),
            str(event.get("status") or ""),
            str(event.get("phase") or ""),
            str(event.get("reason") or ""),
        ]
    )
    return f"{str(skipped).lower()}|{standard}|{shift}|{stable}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    sys.path.insert(0, str(args.source_root.resolve()))

    from auto_ptu.rules.controllers import status_controller

    action_type = status_controller.ActionType
    rows = [
        (
            "fresh_flinch",
            run_case(status_controller, {}, status="Flinch", reason="flinched"),
        ),
        (
            "standard_already_spent",
            run_case(
                status_controller,
                {action_type.STANDARD: "Tackle"},
                status="Confused",
                reason="failed_check",
            ),
        ),
        (
            "both_already_spent",
            run_case(
                status_controller,
                {
                    action_type.STANDARD: "Tackle",
                    action_type.SHIFT: "Retreat",
                },
                status="Paralyzed",
                reason="failed_check",
            ),
        ),
    ]

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{name}\t{value}" for name, value in rows) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} Python status-skip oracle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
