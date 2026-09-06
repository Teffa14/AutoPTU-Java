#!/usr/bin/env python3
"""Freeze ordinary-damage temporary HP absorption from the pinned Python oracle."""

from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path


def _pokemon_state(source_root: Path):
    sys.path.insert(0, str(source_root.resolve()))
    from auto_ptu.data_models import PokemonSpec
    from auto_ptu.rules import PokemonState

    spec = PokemonSpec(
        species="Oracle Dummy",
        level=20,
        types=["Normal"],
        hp_stat=30,
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

    cases = (
        ("partial", 7, 4),
        ("exact", 7, 7),
        ("overflow", 7, 12),
        ("none", 0, 9),
    )

    rows = []
    for case, temporary_hp, incoming_damage in cases:
        pokemon = _pokemon_state(args.source_root)
        pokemon.temp_hp = temporary_hp
        hp_before = int(pokemon.hp)
        pokemon.apply_damage(incoming_damage, skip_injury=True, skip_massive_injury=True)
        hp_after = int(pokemon.hp)
        remaining_temporary_hp = int(pokemon.temp_hp)
        absorbed_damage = temporary_hp - remaining_temporary_hp
        remaining_damage = hp_before - hp_after
        rows.append(
            (
                case,
                temporary_hp,
                incoming_damage,
                max(0, incoming_damage),
                absorbed_damage,
                remaining_damage,
                remaining_temporary_hp,
            )
        )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(
            (
                "case",
                "temporary_hp",
                "incoming_damage",
                "pending_damage",
                "absorbed_damage",
                "remaining_damage",
                "remaining_temporary_hp",
            )
        )
        writer.writerows(rows)


if __name__ == "__main__":
    main()
