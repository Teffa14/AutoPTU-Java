#!/usr/bin/env python3
"""Freeze Attack of Opportunity commit semantics from the pinned Python keyword fixture."""
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

    required_fragments = [
        "as an Interrupt",
        "only once per round",
        "Sleeping, Flinched, or Paralyzed",
    ]
    missing = [fragment for fragment in required_fragments if fragment not in effects]
    if missing:
        raise AssertionError("missing Attack of Opportunity commit contract: " + ", ".join(missing))

    rows = [
        ("owned_ready", "", "0", "COMMITTED", "ELIGIBLE", "1"),
        ("owned_exhausted", "", "1", "BLOCKED", "ROUND_USE_EXHAUSTED", "1"),
        ("sleeping", "Sleeping", "0", "BLOCKED", "BLOCKED_BY_STATUS", "0"),
        ("flinched", "Flinched", "0", "BLOCKED", "BLOCKED_BY_STATUS", "0"),
        ("paralyzed", "Paralyzed", "0", "BLOCKED", "BLOCKED_BY_STATUS", "0"),
    ]

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "case\tstatus\tinitial_uses\texpected_commit\texpected_reason\texpected_final_uses\n"
        + "\n".join("\t".join(row) for row in rows)
        + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(rows)} Attack of Opportunity commit rows to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
