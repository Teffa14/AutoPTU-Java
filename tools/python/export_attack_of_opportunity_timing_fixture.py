#!/usr/bin/env python3
"""Freeze Attack of Opportunity timing semantics from the pinned Python keyword fixture."""
from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = (
        args.source_root.resolve()
        / "auto_ptu"
        / "data"
        / "campaigns"
        / "keyword_demos"
        / "interrupt-1_demo.json"
    )
    payload = json.loads(source.read_text(encoding="utf-8"))
    player = payload["players"][0]
    move = next(move for move in player["moves"] if move["name"] == "Attack of Opportunity")
    effects = move["effects_text"]

    if "as an Interrupt" not in effects:
        raise AssertionError("Attack of Opportunity no longer declares Interrupt timing")

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "reaction_key\texpected_timing\n"
        "attack_of_opportunity\tINTERRUPT\n",
        encoding="utf-8",
    )
    print(f"wrote Attack of Opportunity timing fixture to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
