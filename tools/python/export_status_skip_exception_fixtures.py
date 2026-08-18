#!/usr/bin/env python3
"""Export StatusController Trainer Feature exception behavior from pinned AutoPTU."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from types import SimpleNamespace


class FixtureActor:
    def __init__(self, *, signature=None, duelists_manual=False):
        self.actions_taken = {}
        self.spec = SimpleNamespace(poke_edge_choices={"signature_technique": signature} if signature else {})
        self.controller_id = "trainer"
        self.hp = 10
        self.duelists_manual = duelists_manual
        self.added_effects = []

    def mark_action(self, action_type, detail):
        self.actions_taken[action_type] = detail

    def get_temporary_effects(self, slug):
        if slug == "duelist_manual_ignore_status" and self.duelists_manual:
            return [{"slug": slug}]
        return []

    def add_temporary_effect(self, slug, **kwargs):
        self.added_effects.append((slug, dict(kwargs)))

    def handle_phase_effects(self, *_args, **_kwargs):
        return []


class FixtureBattle:
    def __init__(self, actor, status):
        self._pending_status_skip = {"status": status, "phase": "start", "effect": "failed_check"}
        self.current_actor_id = "actor"
        self.pokemon = {"actor": actor}
        self.round = 1
        self.phase = SimpleNamespace(value="start")
        self.events = []

    def log_event(self, payload):
        self.events.append(dict(payload))


def run_case(status_controller, *, status, signature=None, duelists_manual=False):
    actor = FixtureActor(signature=signature, duelists_manual=duelists_manual)
    battle = FixtureBattle(actor, status)
    skipped = status_controller.StatusController(battle).consume_pending_status_skip()
    event = battle.events[-1]

    kind = "none"
    move = ""
    if event.get("type") == "trainer_feature":
        effect = str(event.get("effect") or "")
        if effect == "supreme_concentration":
            kind = "supreme_concentration"
            move = str(event.get("move") or "")
        elif effect == "ignore_status_skip":
            kind = "duelists_manual"

    return f"{str(skipped).lower()}|{kind}|{move}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    sys.path.insert(0, str(args.source_root.resolve()))

    from auto_ptu.rules.controllers import status_controller

    supreme = {"modification_key": "Supreme Concentration", "move": "Thunderbolt"}
    both = {"modification": "Supreme-Concentration", "move_name": "Psychic"}

    rows = [
        ("supreme_flinch", run_case(status_controller, status="Flinch", signature=supreme)),
        ("supreme_sleep_not_covered", run_case(status_controller, status="Sleep", signature=supreme)),
        ("duelist_confused", run_case(status_controller, status="Confused", duelists_manual=True)),
        ("duelist_flinch_not_covered", run_case(status_controller, status="Flinch", duelists_manual=True)),
        (
            "supreme_priority_when_both_apply",
            run_case(status_controller, status="Confusion", signature=both, duelists_manual=True),
        ),
    ]

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{name}\t{value}" for name, value in rows) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} Python status-skip exception fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
