#!/usr/bin/env python3
"""Freeze the semantic round_start payload through pinned Python PhaseController.start_round()."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path


class InitiativeEntryDouble:
    def __init__(self, actor, controller, speed, trainer_modifier, roll, total):
        self.payload = {
            "actor": actor,
            "controller": controller,
            "speed": speed,
            "trainer_modifier": trainer_modifier,
            "roll": roll,
            "total": total,
        }

    def to_dict(self): return dict(self.payload)


class PokemonDouble:
    def __init__(self, hp, max_hp, statuses, abilities, active, injuries=0):
        self.hp = hp
        self._max_hp = max_hp
        self.statuses = list(statuses)
        self._abilities = list(abilities)
        self.active = active
        self.injuries = injuries
        self.fainted = hp <= 0

    def max_hp(self): return self._max_hp
    def ability_names(self): return list(self._abilities)
    def _normalized_status_name(self, entry): return str(entry).strip().lower()
    def remove_temporary_effect(self, _family): return False
    def add_temporary_effect(self, _family, **_payload): pass
    def is_trainer_combatant(self): return False


class BattleDouble:
    def __init__(self):
        self.round = 1
        self.round_uses = 0
        self.dance_moves_used_this_round = {}
        self.fainted_history = []
        self.trainers = {}
        self.pokemon = {
            "alpha": PokemonDouble(31, 40, ["Burned", "Confused", "Burned"], ["Static", "Sprint"], True, 1),
            "bench": PokemonDouble(22, 35, [], ["Run Away"], False, 0),
        }
        self.declared_actions = []
        self.damage_this_round = set()
        self.damage_taken_from = {}
        self.damage_received_this_round = {}
        self.damage_last_round = set()
        self.damage_taken_from_last_round = {}
        self._injuries_previous_round = {}
        self._injuries_last_round = {}
        self.echoed_voice_rounds = []
        self.fusion_bolt_rounds = []
        self.fusion_flare_rounds = []
        self.initiative_order = []
        self._initiative_index = -1
        self.current_actor_id = None
        self._last_action_actor_id = None
        self.weather = "Rain"
        self.events = []

    def _resolve_dimensional_rifts_end_of_round(self): pass
    def _advance_terrain(self): pass
    def _advance_zone_effects(self): pass
    def _advance_room_effects(self): pass
    def _resolve_delayed_hits(self): pass
    def _clear_expired_follow_me(self): pass
    def _clear_expired_foresight(self): pass
    def _build_initiative_order(self):
        return [
            InitiativeEntryDouble("alpha", "trainer-a", 12, 2, 4, 18),
            InitiativeEntryDouble("trainer-a", "trainer-a", 7, 2, 3, 12),
        ]
    def log_event(self, event): self.events.append(dict(event))
    def _active_ability_holders(self, _name): return []
    def _apply_arena_trap(self): pass
    def _trigger_intimidate(self, _actor): pass
    def _trigger_impostor(self, _actor): pass


def encode_list(values): return ",".join(str(value) for value in values)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    sys.path.insert(0, str(args.source_root.resolve()))
    from auto_ptu.rules.controllers.phase_controller import PhaseController

    battle = BattleDouble()
    PhaseController(battle).start_round()
    events = [event for event in battle.events if event.get("type") == "round_start"]
    if len(events) != 1:
        raise AssertionError(f"expected one round_start event, got {len(events)}")
    event = events[0]

    lines = [f"ROUND_START\t{event['round']}\t{event['weather']}"]
    for entry in event["initiative"]:
        lines.append("\t".join([
            "INITIATIVE", entry["actor"], entry["controller"], str(entry["speed"]),
            str(entry["trainer_modifier"]), str(entry["roll"]), str(entry["total"]),
        ]))
    for entry in event["initial_states"]:
        lines.append("\t".join([
            "COMBATANT", entry["actor"], str(entry["hp"]), str(entry["max_hp"]),
            encode_list(entry["statuses"]), encode_list(entry["abilities"]),
            "true" if entry["active"] else "false",
        ]))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(output.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
