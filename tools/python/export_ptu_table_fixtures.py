#!/usr/bin/env python3
"""Export pure PTU table results from the pinned Python AutoPTU oracle."""
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
    from auto_ptu import ptu_engine

    scenarios: list[tuple[str, str]] = []

    for db in (1, 2, 3, 8, 15, 16, 20):
        n, sides, flat = ptu_engine.db_to_dice(db)
        scenarios.append((f"db_{db}", f"{n},{sides},{flat}"))

    type_cases = [
        ("fire_grass", "Fire", ["Grass"]),
        ("fire_water", "Fire", ["Water"]),
        ("electric_ground", "Electric", ["Ground"]),
        ("normal_normal", "Normal", ["Normal"]),
        ("fire_grass_steel", "Fire", ["Grass", "Steel"]),
        ("fire_water_dragon", "Fire", ["Water", "Dragon"]),
        ("fire_grass_water", "Fire", ["Grass", "Water"]),
        ("ground_fire_flying", "Ground", ["Fire", "Flying"]),
        ("lower_attack_case", "fire", ["Grass"]),
        ("lower_defense_case", "Fire", ["grass"]),
        ("unknown_attack", "Mystery", ["Water"]),
    ]
    for name, attack, defenses in type_cases:
        scenarios.append((name, repr(float(ptu_engine.type_multiplier(attack, defenses)))))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(f"{name}\t{value}" for name, value in scenarios) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(scenarios)} Python PTU table fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
