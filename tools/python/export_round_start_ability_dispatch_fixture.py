#!/usr/bin/env python3
"""Freeze round-start ability orchestration, rollover state, and Intimidate trace parity."""
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
    from auto_ptu.data_models import PokemonSpec
    from auto_ptu.rules import BattleState, GridState, PokemonState, TrainerState
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

    class FakeInitiativeEntry:
        def __init__(self, actor_id: str, score: int) -> None:
            self.actor_id = actor_id
            self.score = score

        def to_dict(self):
            return {"actor_id": self.actor_id, "score": self.score}

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
    battle._build_initiative_order = lambda: [
        FakeInitiativeEntry("actor-b", 20),
        FakeInitiativeEntry("actor-a", 10),
    ]

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

    def real_spec(name: str, *, ability: str | None = None, speed: int = 10) -> PokemonSpec:
        return PokemonSpec(
            species=name,
            level=20,
            types=["Normal"],
            hp_stat=10,
            atk=12,
            defense=10,
            spatk=12,
            spdef=10,
            spd=speed,
            moves=[],
            abilities=[{"name": ability}] if ability else [],
            movement={"overland": 4},
        )

    real_trainers = {
        "players": TrainerState(identifier="players", name="Players", team="players"),
        "foes": TrainerState(identifier="foes", name="Foes", team="foes"),
    }
    real_pokemon = OrderedDict([
        (
            "holder",
            PokemonState(
                spec=real_spec("Holder", ability="Intimidate", speed=20),
                controller_id="players",
                position=(2, 2),
                active=True,
            ),
        ),
        (
            "target",
            PokemonState(
                spec=real_spec("Target", speed=10),
                controller_id="foes",
                position=(2, 3),
                active=True,
            ),
        ),
    ])
    for mon in real_pokemon.values():
        mon.hp = 20
    real_battle = BattleState(
        trainers=real_trainers,
        pokemon=real_pokemon,
        grid=GridState(width=6, height=6),
    )
    real_pokemon["holder"].add_temporary_effect("joined_round", round=1)
    PhaseController(real_battle).start_round()

    intimidate_used = any(
        int(entry.get("round", 0) or 0) == real_battle.round
        for entry in real_pokemon["holder"].get_temporary_effects("intimidate_used")
    )
    real_trace: list[str] = []
    for event in real_battle.log:
        if event.get("type") == "combat_stage" and event.get("move") == "Intimidate":
            real_trace.append("|".join([
                "STAGE",
                str(event.get("actor") or ""),
                str(event.get("target") or ""),
                str(event.get("move") or ""),
                str(event.get("stat") or ""),
                str(event.get("effect") or ""),
                str(event.get("amount") or 0),
                str(event.get("new_stage") or 0),
            ]))
        elif event.get("type") == "ability" and event.get("ability") == "Intimidate":
            real_trace.append("|".join([
                "INTIMIDATE",
                str(event.get("actor") or ""),
                str(event.get("target") or ""),
                str(event.get("effect") or ""),
                str(event.get("move") or ""),
            ]))

    rows = [
        "ROUND_AFTER\t" + str(battle.round),
        "INITIATIVE_ORDER_AFTER\t" + ",".join(entry.actor_id for entry in battle.initiative_order),
        "INITIATIVE_INDEX_AFTER_START_ROUND\t" + str(battle._initiative_index),
        "WEATHER\tRain",
        "WEATHER_AFTER\t" + battle.weather,
        "AIR_LOCK_HOLDERS\tair-two,air-one",
        "AIR_LOCK_EVENTS\t" + ";".join(event_rows),
        "COMBATANTS\tactor-b:true:false;fainted:true:true;bench:false:false;actor-a:true:false",
        "INVOCATIONS\t" + ";".join(invocations),
        "TIMELINE\t" + ",".join(timeline),
        "ROLLOVER_INTIMIDATE_ROUND\t" + str(real_battle.round),
        "ROLLOVER_INTIMIDATE_TARGET_ATK\t" + str(int(real_pokemon["target"].combat_stages.get("atk", 0) or 0)),
        "ROLLOVER_INTIMIDATE_USED\t" + ("1" if intimidate_used else "0"),
        "ROLLOVER_INTIMIDATE_TRACE\t" + ";".join(real_trace),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
