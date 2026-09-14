#!/usr/bin/env python3
"""Freeze Attack of Opportunity trigger families from the pinned Python oracle."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROWS = (
    ("ADJACENT_NON_TARGETING_MANEUVER", "push,grapple,disarm,trip,dirty_trick", "An adjacent foe uses a Push, Grapple, Disarm, Trip, or Dirty Trick Maneuver that does not target you."),
    ("ADJACENT_STAND_UP", "", "An adjacent foe stands up."),
    ("ADJACENT_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET", "", "An adjacent foe uses a Ranged Attack that does not target someone adjacent to it."),
    ("ADJACENT_STANDARD_ITEM_RETRIEVAL", "", "An adjacent foe uses a Standard Action to pick up or retrieve an item."),
    ("ADJACENT_SHIFT_AWAY", "", "An adjacent foe Shifts out of a Square adjacent to you."),
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_root.resolve() / "auto_ptu" / "data" / "campaigns" / "keyword_demos" / "interrupt-1_demo.json"
    payload = json.loads(source.read_text(encoding="utf-8"))
    move = next(move for move in payload["players"][0]["moves"] if move["name"] == "Attack of Opportunity")
    effects = move["effects_text"]

    output_rows = ["reaction_key\tordinal\ttrigger_kind\tqualifiers"]
    for ordinal, (kind, qualifiers, sentence) in enumerate(ROWS):
        if sentence not in effects:
            raise AssertionError(f"Attack of Opportunity trigger text changed: {sentence}")
        output_rows.append(f"attack_of_opportunity\t{ordinal}\t{kind}\t{qualifiers}")

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(output_rows) + "\n", encoding="utf-8")
    print(f"wrote Attack of Opportunity trigger fixture to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
