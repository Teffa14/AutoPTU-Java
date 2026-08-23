#!/usr/bin/env python3
"""Export destination-selection parity from real Perception/Telepathy interrupt hooks."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from types import SimpleNamespace


def encode_coord(coord):
    return f"{coord[0]},{coord[1]}"


def encode_coords(coords):
    return "|".join(encode_coord(coord) for coord in coords)


def run_case(module, *, case, hook_name, origin, reachable, threatened):
    defender = SimpleNamespace(position=origin, hp=30)
    attacker = SimpleNamespace(position=(0, 0))
    battle = SimpleNamespace(
        grid=object(),
        round=3,
        _team_for=lambda _actor_id: "A",
    )
    move = SimpleNamespace(
        name="Oracle Area Move",
        category="Physical",
        area_kind="Burst",
        area_value=1,
        target_kind="Ranged",
        range_kind="Ranged",
        target_range=6,
        range_value=6,
    )
    ctx = SimpleNamespace(
        defender=defender,
        defender_id="defender",
        attacker=attacker,
        attacker_id="attacker",
        battle=battle,
        effective_move=move,
        move=move,
        events=[],
        result={"hit": True, "damage": 9, "type_multiplier": 1.0},
    )

    original_affected = module.targeting.affected_tiles
    original_legal = module.movement.legal_shift_tiles
    original_has_exact = module.has_ability_exact
    try:
        module.targeting.affected_tiles = lambda *_args, **_kwargs: set(threatened)
        module.movement.legal_shift_tiles = lambda *_args, **_kwargs: list(reachable)
        module.has_ability_exact = lambda *_args, **_kwargs: True
        getattr(module, hook_name)(ctx)
    finally:
        module.targeting.affected_tiles = original_affected
        module.movement.legal_shift_tiles = original_legal
        module.has_ability_exact = original_has_exact

    destination = "" if defender.position == origin else encode_coord(defender.position)
    return destination


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.rules.hooks.abilities import pre_damage_interrupts as module

    cases = [
        {
            "case": "telepathy_farthest_first_tie",
            "hook_name": "_telepathy_interrupt",
            "origin": (1, 1),
            "reachable": [(1, 1), (2, 1), (3, 1), (1, 3)],
            "threatened": [(1, 1), (2, 1)],
            "max_distance": "",
        },
        {
            "case": "telepathy_no_safe_tile",
            "hook_name": "_telepathy_interrupt",
            "origin": (1, 1),
            "reachable": [(1, 1), (2, 1)],
            "threatened": [(1, 1), (2, 1)],
            "max_distance": "",
        },
        {
            "case": "telepathy_excludes_threatened_farthest",
            "hook_name": "_telepathy_interrupt",
            "origin": (2, 2),
            "reachable": [(2, 2), (4, 2), (2, 4), (3, 2)],
            "threatened": [(2, 2), (4, 2)],
            "max_distance": "",
        },
        {
            "case": "perception_errata_caps_disengage_to_one",
            "hook_name": "_perception_errata_interrupt",
            "origin": (1, 1),
            "reachable": [(1, 1), (3, 1), (2, 1), (1, 2)],
            "threatened": [(1, 1)],
            "max_distance": "1",
        },
        {
            "case": "perception_errata_no_safe_one_step_tile",
            "hook_name": "_perception_errata_interrupt",
            "origin": (1, 1),
            "reachable": [(1, 1), (3, 1)],
            "threatened": [(1, 1)],
            "max_distance": "1",
        },
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        handle.write("case\torigin\treachable\tthreatened\tmax_distance\tdestination\n")
        for item in cases:
            destination = run_case(module, **{k: item[k] for k in ("case", "hook_name", "origin", "reachable", "threatened")})
            handle.write(
                f"{item['case']}\t{encode_coord(item['origin'])}\t"
                f"{encode_coords(item['reachable'])}\t{encode_coords(item['threatened'])}\t"
                f"{item['max_distance']}\t{destination}\n"
            )


if __name__ == "__main__":
    main()
