#!/usr/bin/env python3
"""Execute Python BattleState terrain/zone/room ROUND_START progression as an oracle."""
from __future__ import annotations

import argparse
import importlib
import sys
from pathlib import Path


class PokemonStub:
    def __init__(self) -> None:
        self.statuses = {"wondered", "burned"}

    def remove_status_by_names(self, names):
        normalized = {str(name).strip().lower() for name in names}
        self.statuses = {status for status in self.statuses if status.lower() not in normalized}


class BattleStub:
    def __init__(self, round_number, terrain, zones, rooms) -> None:
        self.round = round_number
        self.terrain = None if terrain is None else dict(terrain)
        self.zone_effects = [dict(effect) for effect in zones]
        self.room_effects = [dict(effect) for effect in rooms]
        self.pokemon = {"alpha": PokemonStub(), "beta": PokemonStub()}
        self.events = []

    def log_event(self, event):
        self.events.append(dict(event))


def cases():
    return [
        {
            "name": "countdown_and_persistent",
            "round": 3,
            "terrain": {"name": "Grassy Terrain", "remaining": 2},
            "zones": [
                {"name": "Fog Zone", "remaining": 2},
                {"name": "Persistent Zone"},
            ],
            "rooms": [{"name": "Trick Room", "remaining": 2}],
        },
        {
            "name": "ordered_expiry_and_wonder_room_cleanup",
            "round": 5,
            "terrain": {"name": "Electric Terrain", "remaining": 1},
            "zones": [
                {"name": "Hazard Zone", "remaining": 1},
                {"name": "Long Zone", "remaining": 2},
            ],
            "rooms": [
                {"name": "Wonder Room", "remaining": 1},
                {"name": "Other Room", "remaining": 1},
            ],
        },
        {
            "name": "zero_and_negative_expire",
            "round": 7,
            "terrain": {"name": "Zero Terrain", "remaining": 0},
            "zones": [{"name": "Expired Zone", "remaining": -2}],
            "rooms": [],
        },
    ]


def encode_entry(entry):
    if entry is None:
        return ""
    remaining = entry.get("remaining")
    return f"{entry.get('name', '')}~{'' if remaining is None else remaining}"


def encode_many(entries):
    return ";".join(encode_entry(entry) for entry in entries)


def encode_events(events):
    return ";".join(
        f"{event.get('type', '')}|{event.get('effect', '')}|{event.get('name', '')}|{event.get('round', '')}"
        for event in events
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    sys.path.insert(0, str(source_root))
    battle_state = importlib.import_module("auto_ptu.rules.battle_state")

    rows = [
        "name\tround\tinput_terrain\tinput_zones\tinput_rooms\toutput_terrain\toutput_zones\toutput_rooms\tevents\twondered_removed\tburned_preserved"
    ]
    for case in cases():
        battle = BattleStub(case["round"], case["terrain"], case["zones"], case["rooms"])
        battle_state.BattleState._advance_terrain(battle)
        battle_state.BattleState._advance_zone_effects(battle)
        battle_state.BattleState._advance_room_effects(battle)
        rows.append("\t".join([
            case["name"],
            str(case["round"]),
            encode_entry(case["terrain"]),
            encode_many(case["zones"]),
            encode_many(case["rooms"]),
            encode_entry(battle.terrain),
            encode_many(battle.zone_effects),
            encode_many(battle.room_effects),
            encode_events(battle.events),
            "1" if all("wondered" not in pokemon.statuses for pokemon in battle.pokemon.values()) else "0",
            "1" if all("burned" in pokemon.statuses for pokemon in battle.pokemon.values()) else "0",
        ]))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote {len(rows) - 1} Python field round progression fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
