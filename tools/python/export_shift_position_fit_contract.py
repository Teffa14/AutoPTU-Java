#!/usr/bin/env python3
"""Freeze the pinned Python Shift landing/occupancy contract for Java parity CI."""

from __future__ import annotations

import argparse
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    root = Path(args.source_root)
    battle_state = (root / "auto_ptu" / "rules" / "battle_state.py").read_text(encoding="utf-8")
    movement = (root / "auto_ptu" / "rules" / "movement.py").read_text(encoding="utf-8")

    required_battle = {
        "candidate_tiles": "candidate_tiles = actor.footprint_tiles(destination)",
        "in_bounds": "if any(not self.grid.in_bounds(tile) for tile in candidate_tiles):",
        "blocking_names": 'blocked_names = {"wall", "blocker", "blocking", "void"}',
        "self_exclusion": "exclude_id=(exclude_id if exclude_id is not None else actor_id)",
        "active_default": "active_only: bool = True",
        "conscious_default": "conscious_only: bool = True",
        "terrain_default": "block_on_terrain: bool = True",
        "disjoint": "return candidate_tiles.isdisjoint(occupied)",
    }
    required_movement = {
        "server_fit_call": "battle._position_can_fit(actor_id, nxt)",
        "movement_profile": "actor.movement_profile()",
    }

    for key, snippet in required_battle.items():
        if snippet not in battle_state:
            raise SystemExit(f"Pinned Python position-fit contract changed: missing {key}: {snippet}")
    for key, snippet in required_movement.items():
        if snippet not in movement:
            raise SystemExit(f"Pinned Python Shift contract changed: missing {key}: {snippet}")

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    rows = [
        ("position_fit.footprint", "actor.footprint_tiles(destination)"),
        ("position_fit.out_of_bounds", "reject"),
        ("position_fit.blocking_tile_types", "wall,blocker,blocking,void"),
        ("position_fit.default.exclude_self", "true"),
        ("position_fit.default.active_only", "true"),
        ("position_fit.default.conscious_only", "true"),
        ("position_fit.default.block_on_terrain", "true"),
        ("shift.position_fit_source", "battle_state"),
        ("shift.profile_source", "actor"),
    ]
    out.write_text("key\tvalue\n" + "".join(f"{k}\t{v}\n" for k, v in rows), encoding="utf-8")


if __name__ == "__main__":
    main()
