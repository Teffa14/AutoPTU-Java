#!/usr/bin/env python3
"""Freeze round-window history pruning through pinned Python PhaseController.start_round()."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path


class BattleDouble:
    def __init__(self, current_round: int, echoed: list[int], bolt: list[int], flare: list[int]):
        self.round = current_round - 1
        self.round_uses = 0
        self.dance_moves_used_this_round = {}
        self.fainted_history = []
        self.trainers = {}
        self.pokemon = {}
        self.declared_actions = []
        self.damage_this_round = set()
        self.damage_taken_from = {}
        self.damage_received_this_round = {}
        self.damage_last_round = set()
        self.damage_taken_from_last_round = {}
        self._injuries_previous_round = {}
        self._injuries_last_round = {}
        self.echoed_voice_rounds = list(echoed)
        self.fusion_bolt_rounds = list(bolt)
        self.fusion_flare_rounds = list(flare)
        self.initiative_order = []
        self._initiative_index = -1
        self.current_actor_id = None
        self._last_action_actor_id = None
        self.weather = "Clear"
        self.events = []

    def _resolve_dimensional_rifts_end_of_round(self):
        pass

    def _advance_terrain(self):
        pass

    def _advance_zone_effects(self):
        pass

    def _advance_room_effects(self):
        pass

    def _resolve_delayed_hits(self):
        pass

    def _clear_expired_follow_me(self):
        pass

    def _clear_expired_foresight(self):
        pass

    def _build_initiative_order(self):
        return []

    def log_event(self, event):
        self.events.append(dict(event))

    def _active_ability_holders(self, _name):
        return []

    def _apply_arena_trap(self):
        pass


def csv(values: list[int]) -> str:
    return ",".join(str(value) for value in values)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    sys.path.insert(0, str(args.source_root.resolve()))
    from auto_ptu.rules.controllers.phase_controller import PhaseController

    cases = [
        ("window_boundaries", 5, [1, 2, 3, 4, 5], [1, 2, 3, 4, 5], [1, 2, 3, 4, 5]),
        ("order_duplicates", 5, [1, 3, 4, 3, 5], [3, 5, 4, 5], [4, 3, 4, 5]),
        ("early_round", 1, [0, 1], [0, 1], [0, 1]),
    ]

    lines = []
    for name, current_round, echoed, bolt, flare in cases:
        battle = BattleDouble(current_round, echoed, bolt, flare)
        PhaseController(battle).start_round()
        if battle.round != current_round:
            raise AssertionError(f"expected round {current_round}, got {battle.round}")
        lines.append(
            "\t".join(
                [
                    "ROUND_WINDOW_HISTORY",
                    name,
                    str(current_round),
                    csv(echoed),
                    csv(battle.echoed_voice_rounds),
                    csv(bolt),
                    csv(battle.fusion_bolt_rounds),
                    csv(flare),
                    csv(battle.fusion_flare_rounds),
                ]
            )
        )

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(output.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
