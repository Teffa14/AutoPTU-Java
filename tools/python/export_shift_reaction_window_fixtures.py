#!/usr/bin/env python3
"""Freeze ordered Shift-away reaction-window discovery through the pinned Python targeting oracle."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


def encode_coord(coord):
    return f"{coord[0]},{coord[1]}"


def encode_reactors(reactors):
    return ";".join(f"{combatant_id}@{encode_coord(anchor)}@{size}" for combatant_id, anchor, size in reactors)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.rules import targeting

    cases = [
        (
            "ordered_two_reactors",
            [("beta", (2, 2), "Medium"), ("alpha", (1, 1), "Medium"), ("near_destination", (4, 2), "Medium")],
            "foe",
            (2, 1),
            (4, 1),
            "Medium",
        ),
        (
            "stays_adjacent",
            [("reactor", (1, 1), "Medium")],
            "foe",
            (2, 1),
            (2, 2),
            "Medium",
        ),
        (
            "large_reactor",
            [("large", (2, 2), "Large"), ("far", (8, 8), "Medium")],
            "foe",
            (4, 2),
            (5, 2),
            "Medium",
        ),
        (
            "large_shifted_foe",
            [("reactor", (1, 1), "Medium")],
            "large_foe",
            (2, 1),
            (3, 1),
            "Large",
        ),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        handle.write("case\treactors\tshifted_id\tbefore\tafter\tshifted_size\texpected_reactors\n")
        for case, reactors, shifted_id, before, after, shifted_size in cases:
            expected = []
            for combatant_id, anchor, size in reactors:
                before_distance = targeting.footprint_distance(anchor, size, before, shifted_size)
                after_distance = targeting.footprint_distance(anchor, size, after, shifted_size)
                if before_distance == 1 and after_distance > 1:
                    expected.append(combatant_id)
            handle.write(
                f"{case}\t{encode_reactors(reactors)}\t{shifted_id}\t{encode_coord(before)}\t"
                f"{encode_coord(after)}\t{shifted_size}\t{';'.join(expected)}\n"
            )


if __name__ == "__main__":
    main()
