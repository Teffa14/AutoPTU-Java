#!/usr/bin/env python3
"""Freeze ordered switch mutation stages from the pinned Python _apply_switch source."""
from __future__ import annotations

import argparse
import inspect
import re
import sys
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.rules import BattleState

    source = inspect.getsource(BattleState._apply_switch)
    patterns = [
        ("DEACTIVATE_OUTGOING", r"outgoing\.active\s*=\s*False"),
        ("REMOVE_OUTGOING_PRESENCE", r"outgoing\.position\s*=\s*None"),
        ("ACTIVATE_REPLACEMENT", r"replacement\.active\s*=\s*True"),
        ("PLACE_REPLACEMENT", r"replacement\.position\s*="),
        ("ADD_RELEASED_FROM_BALL", r"add_temporary_effect\(\s*[\"']released_from_ball[\"']"),
        ("ADD_JOINED_ROUND", r"add_temporary_effect\(\s*[\"']joined_round[\"']"),
        ("DISPATCH_COMBATANT_ENTRY", r"_trigger_impostor\("),
    ]

    located = []
    for label, pattern in patterns:
        match = re.search(pattern, source)
        if not match:
            raise RuntimeError(f"pinned _apply_switch no longer exposes expected stage {label}: {pattern}")
        located.append((match.start(), label))

    ordered = [label for _, label in sorted(located)]
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("ORDER\t" + ",".join(ordered) + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
