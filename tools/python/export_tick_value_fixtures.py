#!/usr/bin/env python3
"""Freeze PokemonState.tick_value from the pinned Python AutoPTU oracle."""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path


def _pokemon_state(source_root: Path, hp_stat: int):
    sys.path.insert(0, str(source_root.resolve()))
    from auto_ptu.data_models import PokemonSpec
    from auto_ptu.rules import PokemonState

    spec = PokemonSpec(
        species="Oracle Dummy",
        level=20,
        types=["Normal"],
        hp_stat=hp_stat,
        atk=10,
        defense=10,
        spatk=10,
        spdef=10,
        spd=10,
        moves=[],
        abilities=[],
        items=[],
        movement={"overland": 4},
        weight=5,
        gender="",
    )
    return PokemonState(
        spec=spec,
        controller_id="oracle",
        position=(0, 0),
        active=True,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    # Use actual PokemonState instances so both max_hp() and tick_value() execute in the oracle.
    hp_stats = (1, 5, 10, 19, 30, 55, 100)
    rows = []
    for hp_stat in hp_stats:
        pokemon = _pokemon_state(args.source_root, hp_stat)
        rows.append((hp_stat, int(pokemon.max_hp()), int(pokemon.tick_value())))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(("hp_stat", "max_hp", "tick_value"))
        writer.writerows(rows)


if __name__ == "__main__":
    main()
