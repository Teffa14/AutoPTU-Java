#!/usr/bin/env python3
"""Freeze imported Interrupt/Priority keyword metadata from the pinned Python oracle."""
from __future__ import annotations

import argparse
import json
from pathlib import Path


CASES = (
    ("interrupt-1_demo.json", "Attack of Opportunity", "interrupt-1", "INTERRUPT", 1, True),
    ("interrupt-2_demo.json", "Conversion 2", "interrupt-2", "INTERRUPT", 2, False),
    ("priority-2_demo.json", "Winning Knuckle", "priority-2", "PRIORITY", 2, False),
    ("priority-20_demo.json", "Ally Switch", "priority-20", "PRIORITY", 20, True),
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    demos = (
        args.source_root.resolve()
        / "auto_ptu"
        / "data"
        / "campaigns"
        / "keyword_demos"
    )

    rows = ["case\tkeyword\tfamily\trank\tdeclares_interrupt_text"]
    for filename, move_name, keyword, family, rank, declares_interrupt in CASES:
        payload = json.loads((demos / filename).read_text(encoding="utf-8"))
        move = next(move for move in payload["players"][0]["moves"] if move["name"] == move_name)
        if keyword not in move.get("keywords", []):
            raise AssertionError(f"{move_name} no longer exposes {keyword}")
        actual_declares_interrupt = "as an Interrupt" in move.get("effects_text", "")
        if actual_declares_interrupt != declares_interrupt:
            raise AssertionError(
                f"{move_name} Interrupt text changed: expected {declares_interrupt}, got {actual_declares_interrupt}"
            )
        rows.append(
            f"{move_name}\t{keyword}\t{family}\t{rank}\t{str(declares_interrupt).lower()}"
        )

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote imported timing keyword fixture to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
