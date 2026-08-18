#!/usr/bin/env python3
"""Export pinned Python Burn damage behavior for Java parity tests."""
from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path
from types import SimpleNamespace


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.rules import calculations

    cases = [
        ("physical_even_burned", 20, "Physical", True),
        ("physical_odd_burned", 21, "Physical", True),
        ("physical_clean", 21, "Physical", False),
        ("special_burned", 21, "Special", True),
        ("status_burned", 21, "Status", True),
        ("physical_lowercase_burned", 17, "physical", True),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(["name", "base_damage", "category", "burned", "expected_damage"])
        for name, base_damage, category, burned in cases:
            pokemon = _PokemonStub(burned)
            move = SimpleNamespace(category=category)
            expected = calculations.apply_status_modifiers(base_damage, pokemon, move)
            writer.writerow([name, base_damage, category, str(burned).lower(), expected])


class _PokemonStub:
    def __init__(self, burned: bool) -> None:
        self._burned = burned

    def has_status(self, name: str) -> bool:
        return self._burned and str(name).strip().lower() == "burned"


if __name__ == "__main__":
    main()
