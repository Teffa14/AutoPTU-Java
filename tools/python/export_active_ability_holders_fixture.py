#!/usr/bin/env python3
"""Freeze active ability-holder resolution from the pinned Python oracle."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = str(Path(args.source_root).resolve())
    sys.path.insert(0, source_root)

    from auto_ptu.data_models import PokemonSpec
    from auto_ptu.rules import BattleState, GridState, PokemonState, TrainerState

    def spec(name: str, ability: str | None = None) -> PokemonSpec:
        return PokemonSpec(
            species=name,
            level=20,
            types=["Normal"],
            hp_stat=10,
            atk=12,
            defense=10,
            spatk=12,
            spdef=10,
            spd=10,
            moves=[],
            abilities=[{"name": ability}] if ability else [],
            movement={"overland": 4},
        )

    trainers = {
        "a": TrainerState(identifier="a", name="A", team="players"),
        "b": TrainerState(identifier="b", name="B", team="foes"),
    }
    pokemon = {
        "arena-first": PokemonState(spec=spec("ArenaFirst", "Arena Trap"), controller_id="a", position=(1, 1), active=True),
        "air": PokemonState(spec=spec("Air", "Air Lock"), controller_id="a", position=(2, 1), active=True),
        "arena-inactive": PokemonState(spec=spec("ArenaInactive", "Arena Trap"), controller_id="a", position=None, active=False),
        "arena-fainted": PokemonState(spec=spec("ArenaFainted", "Arena Trap"), controller_id="b", position=(3, 1), active=True),
        "arena-second": PokemonState(spec=spec("ArenaSecond", "Arena Trap"), controller_id="b", position=(4, 1), active=True),
    }
    pokemon["arena-fainted"].hp = 0

    battle = BattleState(trainers=trainers, pokemon=pokemon, grid=GridState(width=8, height=8))
    rows = [
        "ARENA_TRAP\t" + ",".join(battle._active_ability_holders("Arena Trap")),
        "AIR_LOCK\t" + ",".join(battle._active_ability_holders("Air Lock")),
        "MISSING\t" + ",".join(battle._active_ability_holders("Missing Ability")),
    ]
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
