#!/usr/bin/env python3
"""Freeze server-owned ordinary damage ingress from the pinned Python oracle."""

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
        ("temp_partial", 20, 7, 4),
        ("temp_overflow", 20, 7, 12),
        ("hp_only", 20, 0, 9),
        ("overkill", 3, 2, 50),
    )

    rows = []
    for case, hp_before, temp_hp_before, incoming_damage in cases:
        pokemon = _pokemon_state(args.source_root)
        pokemon.hp = hp_before
        pokemon.temp_hp = temp_hp_before
        pokemon.apply_damage(incoming_damage, skip_injury=True, skip_massive_injury=True)
        hp_after = int(pokemon.hp)
        temp_hp_after = int(pokemon.temp_hp)
        rows.append(
            (
                case,
                hp_before,
                temp_hp_before,
                incoming_damage,
                max(0, incoming_damage),
                temp_hp_before - temp_hp_after,
                hp_before - hp_after,
                hp_after,
                temp_hp_after,
            )
        )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(
            (
                "case",
                "hp_before",
                "temp_hp_before",
                "incoming_damage",
                "pending_damage",
                "absorbed_damage",
                "hp_damage",
                "hp_after",
                "temp_hp_after",
            )
        )
        writer.writerows(rows)


if __name__ == "__main__":
    main()
