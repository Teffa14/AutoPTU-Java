#!/usr/bin/env python3
"""Export core PTU STAB behavior from pinned Python AutoPTU."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from types import SimpleNamespace


class FixturePokemon:
    def __init__(self, types: tuple[str, ...]):
        self.spec = SimpleNamespace(types=list(types))

    def has_ability(self, _name: str) -> bool:
        return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    sys.path.insert(0, str(args.source_root.resolve()))

    from auto_ptu.data_models import MoveSpec
    from auto_ptu.rules import calculations

    cases = [
        ("same_type", MoveSpec.from_dict({"name": "Water Gun", "type": "Water", "db": 5}), ("Water",)),
        ("off_type", MoveSpec.from_dict({"name": "Water Gun", "type": "Water", "db": 5}), ("Fire",)),
        ("dual_type_match", MoveSpec.from_dict({"name": "Flamethrower", "type": "Fire", "db": 7}), ("Flying", "Fire")),
        ("case_insensitive", MoveSpec.from_dict({"name": "Thunder Shock", "type": "Electric", "db": 4}), ("eLeCtRiC",)),
        ("struggle_no_stab", MoveSpec.from_dict({"name": "Struggle", "type": "Normal", "db": 4}), ("Normal",)),
        ("struggle_plus_no_stab", MoveSpec.from_dict({"name": "Struggle+", "type": "Normal", "db": 6}), ("Normal",)),
    ]

    rows: list[str] = []
    for case_id, move, types in cases:
        result = calculations.stab_db(move, FixturePokemon(types))
        rows.append(f"{case_id}\t{int(result)}\n")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("".join(rows), encoding="utf-8")
    print(f"wrote {len(rows)} Python STAB fixtures to {args.output.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
