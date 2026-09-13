#!/usr/bin/env python3
"""Freeze Shift-away adjacency transitions through the pinned Python targeting oracle."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


def encode(coord):
    return f"{coord[0]},{coord[1]}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.rules import targeting

    cases = [
        ("medium_adjacent_to_away", (1, 1), "Medium", (2, 1), (3, 1), "Medium"),
        ("medium_stays_adjacent", (1, 1), "Medium", (2, 1), (2, 2), "Medium"),
        ("medium_starts_nonadjacent", (1, 1), "Medium", (3, 1), (4, 1), "Medium"),
        ("large_reactor_adjacent_to_away", (2, 2), "Large", (4, 2), (5, 2), "Medium"),
        ("large_foe_stays_adjacent", (1, 1), "Medium", (3, 1), (3, 2), "Large"),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        handle.write("case\treactor\treactor_size\tbefore\tafter\tfoe_size\ttriggered\n")
        for case, reactor, reactor_size, before, after, foe_size in cases:
            before_distance = targeting.footprint_distance(reactor, reactor_size, before, foe_size)
            after_distance = targeting.footprint_distance(reactor, reactor_size, after, foe_size)
            triggered = before_distance == 1 and after_distance > 1
            handle.write(
                f"{case}\t{encode(reactor)}\t{reactor_size}\t{encode(before)}\t{encode(after)}\t"
                f"{foe_size}\t{str(triggered).lower()}\n"
            )


if __name__ == "__main__":
    main()
