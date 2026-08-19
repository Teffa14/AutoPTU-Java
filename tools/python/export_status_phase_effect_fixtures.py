#!/usr/bin/env python3
"""Export concrete status phase behavior from the pinned Python AutoPTU oracle."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from types import SimpleNamespace


class FixtureBattle:
    def __init__(self, round_number: int):
        self.round = round_number
        self.events = []
        self.rng = __import__("random").Random(1)

    def log_event(self, payload):
        self.events.append(dict(payload))


class FixtureActor:
    """Small fixture object that delegates PokemonState helper methods to the oracle class."""

    pokemon_state_type = None

    def __init__(self, statuses):
        self.statuses = list(statuses)
        self.temporary_effects = []
        self.hp = 20
        self.max_hp = 20
        self.temp_hp = 0
        self.injuries = 0
        self.combat_stages = {"atk": 0, "def": 0, "spatk": 0, "spdef": 0, "spd": 0}
        self.spec = SimpleNamespace(abilities=[], ability="", types=[], poke_edge_choices={})
        self.controller_id = "trainer"

    def __getattr__(self, name):
        attr = getattr(self.pokemon_state_type, name)
        if hasattr(attr, "__get__"):
            return attr.__get__(self, type(self))
        return attr


def stable_event(payload):
    return "|".join(
        [
            str(payload.get("type") or ""),
            str(payload.get("actor") or ""),
            str(payload.get("status") or ""),
            str(payload.get("phase") or ""),
            str(payload.get("effect") or ""),
            str(bool(payload.get("skip_turn"))).lower(),
        ]
    )


def run_case(battle_state, status_name: str):
    FixtureActor.pokemon_state_type = battle_state.PokemonState
    actor = FixtureActor([{"name": status_name, "applied_round": 1}])
    battle = FixtureBattle(round_number=1)
    events = battle_state.PokemonState.handle_phase_effects(
        actor, battle, battle_state.TurnPhase.START, "actor"
    )
    flinch_events = [event for event in events if event.get("effect") == "flinch"]
    if len(flinch_events) != 1:
        raise RuntimeError(f"expected one flinch event, got {events!r}")
    return stable_event(flinch_events[0])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    sys.path.insert(0, str(args.source_root.resolve()))

    from auto_ptu.rules import battle_state

    rows = [
        ("flinch", run_case(battle_state, "Flinch")),
        ("flinched_alias", run_case(battle_state, "Flinched")),
    ]

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{name}\t{value}" for name, value in rows) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} Python status-phase oracle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
