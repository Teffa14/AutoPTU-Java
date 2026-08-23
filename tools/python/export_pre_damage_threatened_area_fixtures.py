#!/usr/bin/env python3
"""Export PRE-damage threatened-area geometry from the pinned Python targeting rules."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from types import SimpleNamespace


def encode_coord(coord):
    return f"{coord[0]},{coord[1]}"


def encode_coords(coords):
    return "|".join(encode_coord(coord) for coord in sorted(coords))


def encode_optional(value):
    return "" if value is None else str(value)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.rules import targeting

    class Grid:
        def __init__(self, width, height):
            self.width = width
            self.height = height

        def in_bounds(self, coord):
            return 0 <= coord[0] < self.width and 0 <= coord[1] < self.height

    cases = [
        dict(case="no_area_is_not_threatened", width=8, height=8, attacker=(1, 1), defender=(3, 1),
             target_kind="Ranged", range_kind="Ranged", target_range=6, range_value=6,
             area_kind=None, area_value=None, range_text="Ranged"),
        dict(case="burst_centers_on_defender", width=8, height=8, attacker=(1, 1), defender=(3, 3),
             target_kind="Ranged", range_kind="Ranged", target_range=6, range_value=6,
             area_kind="Burst", area_value=1, range_text="Burst 1"),
        dict(case="line_uses_attacker_to_defender", width=8, height=8, attacker=(1, 1), defender=(4, 1),
             target_kind="Ranged", range_kind="Ranged", target_range=6, range_value=6,
             area_kind="Line", area_value=3, range_text="Line 3"),
        dict(case="cone_uses_attacker_to_defender", width=8, height=8, attacker=(2, 2), defender=(4, 3),
             target_kind="Ranged", range_kind="Ranged", target_range=6, range_value=6,
             area_kind="Cone", area_value=3, range_text="Cone 3"),
        dict(case="closeblast_is_adjacent_to_attacker", width=8, height=8, attacker=(3, 3), defender=(4, 3),
             target_kind="Self", range_kind="Self", target_range=0, range_value=0,
             area_kind="Close Blast", area_value=2, range_text="Close Blast 2"),
        dict(case="burst_clips_to_grid_like_python", width=4, height=4, attacker=(0, 0), defender=(0, 0),
             target_kind="Ranged", range_kind="Ranged", target_range=6, range_value=6,
             area_kind="Burst", area_value=2, range_text="Burst 2"),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        handle.write(
            "case\twidth\theight\tattacker\tdefender\ttarget_kind\trange_kind\ttarget_range\t"
            "range_value\tarea_kind\tarea_value\trange_text\tthreatened\n"
        )
        for item in cases:
            move = SimpleNamespace(
                target_kind=item["target_kind"],
                range_kind=item["range_kind"],
                target_range=item["target_range"],
                range_value=item["range_value"],
                area_kind=item["area_kind"],
                area_value=item["area_value"],
                range_text=item["range_text"],
            )
            area_kind = targeting.normalized_area_kind(move)
            threatened = set()
            if area_kind:
                threatened = targeting.affected_tiles(
                    Grid(item["width"], item["height"]),
                    item["attacker"],
                    item["defender"],
                    move,
                )
            handle.write(
                f"{item['case']}\t{item['width']}\t{item['height']}\t{encode_coord(item['attacker'])}\t"
                f"{encode_coord(item['defender'])}\t{item['target_kind']}\t{item['range_kind']}\t"
                f"{encode_optional(item['target_range'])}\t{encode_optional(item['range_value'])}\t"
                f"{encode_optional(item['area_kind'])}\t{encode_optional(item['area_value'])}\t"
                f"{item['range_text']}\t{encode_coords(threatened)}\n"
            )


if __name__ == "__main__":
    main()
