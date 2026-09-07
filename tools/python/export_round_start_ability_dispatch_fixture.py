#!/usr/bin/env python3
"""Freeze round-start ability orchestration and Air Lock effect parity from pinned Python."""
from __future__ import annotations

import argparse
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
    from auto_ptu.rules.controllers.phase_controller import PhaseController

    timeline: list[str] = []
    air_lock_events: list[dict] = []

    class FakePokemon:
        def __init__(self, *, active: bool, fainted: bool) -> None:
            self.active = active
            self.fainted = fainted
            self.hp = 40
            self.injuries = 0
            self.statuses = []

        def add_temporary_effect(self, *_args, **_kwargs):
            return None

        def remove_temporary_effect(self, *_args, **_kwargs):
            return False

        def _normalized_status_name(self, entry):
            return str(entry)

        def max_hp(self):
            return 40

        def ability_names(self):
            return []

    class FeatureDispatcher:
        def trigger(self, trigger, **_kwargs):
            timeline.append(f"trainer_feature:{trigger}")
            return []

    pokemon = OrderedDict([
        ("actor-b", FakePokemon(active=True, fainted=False)),
        ("fainted", FakePokemon(active=True, fainted=True)),
        ("bench", FakePokemon(active=False, fainted=False)),
        ("actor-a", FakePokemon(active=True, fainted=False)),
    ])

    battle = SimpleNamespace(
        round=0,
        round_uses=99,
        dance_moves_used_this_round={"old": 1},
        fainted_history=[],
        trainers=OrderedDict(),
        pokemon=pokemon,
        declared_actions=[{"old": True}],
        initiative_order=[],
        _initiative_index=7,
        phase=None,
        current_actor_id="old",
        _last_action_actor_id="old",
        damage_last_round=set(),
        damage_this_round=set(),
        damage_taken_from_last_round={},
        damage_taken_from={},
        damage_received_this_round=set(),
        _injuries_previous_round={},
        _injuries_last_round={},
        echoed_voice_rounds=[],
        fusion_bolt_rounds=[],
        fusion_flare_rounds=[],
        weather="Rain",
        trainer_feature_dispatcher=FeatureDispatcher(),
    )

    battle._advance_terrain = lambda: None
    battle._advance_zone_effects = lambda: None
    battle._advance_room_effects = lambda: None
    battle._resolve_delayed_hits = lambda: None
    battle._clear_expired_follow_me = lambda: None
    battle._clear_expired_foresight = lambda: None
    battle._apply_send_out_trainer_feature_effects = lambda *_args, **_kwargs: None
    battle._build_initiative_order = lambda: []

    def log_event(event):
        if event.get("type") == "round_start":
            timeline.append("round_start_event")
        elif event.get("type") == "ability" and event.get("ability") == "Air Lock":
            timeline.append(f"air_lock:{event.get('actor')}")
            air_lock_events.append(dict(event))

    battle.log_event = log_event

    def active_ability_holders(name):
        timeline.append(f"ability_query:{name}")
        if name == "Air Lock":
            return ["air-two", "air-one"]
        return []

    battle._active_ability_holders = active_ability_holders
    battle._apply_arena_trap = lambda: timeline.append("arena_trap")
    battle._trigger_intimidate = lambda actor_id: timeline.append(f"intimidate:{actor_id}")
    battle._trigger_impostor = lambda actor_id: timeline.append(f"impostor:{actor_id}")

    PhaseController(battle).start_round()

    expected_timeline = [
        "round_start_event",
        "trainer_feature:round_start",
        "ability_query:Air Lock",
        "air_lock:air-two",
        "air_lock:air-one",
        "arena_trap",
        "intimidate:actor-b",
        "impostor:actor-b",
        "intimidate:actor-a",
        "impostor:actor-a",
    ]
    if timeline != expected_timeline:
        raise AssertionError(f"unexpected round-start ability timeline: {timeline!r}")
    expected_events = [
        {
            "type": "ability",
            "actor": "air-two",
            "ability": "Air Lock",
            "effect": "weather_suppress",
            "weather": "Rain",
            "description": "Air Lock suppresses the active weather.",
        },
        {
            "type": "ability",
            "actor": "air-one",
            "ability": "Air Lock",
            "effect": "weather_suppress",
            "weather": "Rain",
            "description": "Air Lock suppresses the active weather.",
        },
    ]
    if air_lock_events != expected_events:
        raise AssertionError(f"unexpected Air Lock events: {air_lock_events!r}")
    if battle.weather != "Rain":
        raise AssertionError(f"Air Lock unexpectedly mutated weather: {battle.weather!r}")

    invocations = [
        "air_lock|ABILITY_HOLDER|air-two",
        "air_lock|ABILITY_HOLDER|air-one",
        "arena_trap|GLOBAL|",
        "intimidate|ACTIVE_ACTOR|actor-b",
        "impostor|ACTIVE_ACTOR|actor-b",
        "intimidate|ACTIVE_ACTOR|actor-a",
        "impostor|ACTIVE_ACTOR|actor-a",
    ]
    event_rows = [
        "|".join([
            event["actor"],
            event["ability"],
            event["effect"],
            event["weather"],
            event["description"],
        ])
        for event in air_lock_events
    ]
    rows = [
        "WEATHER\tRain",
        "WEATHER_AFTER\t" + battle.weather,
        "AIR_LOCK_HOLDERS\tair-two,air-one",
        "AIR_LOCK_EVENTS\t" + ";".join(event_rows),
        "COMBATANTS\tactor-b:true:false;fainted:true:true;bench:false:false;actor-a:true:false",
        "INVOCATIONS\t" + ";".join(invocations),
        "TIMELINE\t" + ",".join(timeline),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
