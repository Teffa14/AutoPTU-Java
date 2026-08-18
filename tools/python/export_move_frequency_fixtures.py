#!/usr/bin/env python3
"""Export PTU move-frequency parsing from pinned Python AutoPTU."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    sys.path.insert(0, str(args.source_root.resolve()))

    from auto_ptu.rules.frequency import parse_move_frequency

    cases = [
        ("blank", None),
        ("at_will", "At-Will"),
        ("scene", "Scene"),
        ("scene_x3", "Scene x3"),
        ("scene_whitespace_case", "  SCENE   x  2  "),
        ("daily", "Daily"),
        ("daily_x2", "Daily x2"),
        ("eot", "EOT"),
    ]

    rows: list[str] = []
    for case_id, raw in cases:
        definition = parse_move_frequency(raw)
        if definition is None:
            value = "none"
        else:
            value = f"{definition.slug}|{definition.limit}|{definition.scope}"
        rows.append(f"{case_id}\t{value}\n")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("".join(rows), encoding="utf-8")
    print(f"wrote {len(rows)} Python move-frequency fixtures to {args.output.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
