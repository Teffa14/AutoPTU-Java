#!/usr/bin/env python3
"""Export intrinsic damaging-move metadata from the pinned Python MoveSpec oracle."""
from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))
    from auto_ptu.data_models import MoveSpec

    cases = [
        ("defaults", MoveSpec(name="Oracle Default", type="Normal")),
        (
            "physical_custom",
            MoveSpec.from_dict(
                {
                    "name": "Oracle Physical",
                    "type": "Normal",
                    "category": "Physical",
                    "db": 10,
                    "ac": 5,
                    "crit_range": 18,
                }
            ),
        ),
        (
            "always_hit_special",
            MoveSpec.from_dict(
                {
                    "name": "Oracle Special",
                    "type": "Psychic",
                    "category": "Special",
                    "db": 6,
                    "ac": None,
                    "crit_range": 20,
                }
            ),
        ),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, delimiter="\t")
        writer.writerow(["case", "ac", "damage_base", "crit_range", "damage_category"])
        for case_id, move in cases:
            writer.writerow(
                [
                    case_id,
                    "" if move.ac is None else move.ac,
                    move.db,
                    move.crit_range,
                    str(move.category or "").strip().lower(),
                ]
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
