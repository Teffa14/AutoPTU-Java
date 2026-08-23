#!/usr/bin/env python3
"""Temporary diagnostic: print the pinned Python combat-stage prevention window."""
from __future__ import annotations

import argparse
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    args = parser.parse_args()
    source = (args.source_root / "auto_ptu" / "rules" / "battle_state.py").read_text(encoding="utf-8")
    flower = source.find("flower_veil_blocker = self._flower_veil_blocker(target_id)")
    start = source.find("if not abilities_suppressed", flower)
    if start < 0:
        raise RuntimeError("combat-stage ability prevention start not found")
    candidates = [
        source.find("old_stage", start),
        source.find("current_stage", start),
        source.find("combat_stages", start + 100),
        source.find("applied_delta", start),
    ]
    ends = [x for x in candidates if x > start]
    end = min(ends) if ends else min(len(source), start + 5000)
    snippet = source[start:end]
    raise RuntimeError("PINNED_COMBAT_STAGE_PREVENTION_WINDOW\n" + snippet)


if __name__ == "__main__":
    main()
