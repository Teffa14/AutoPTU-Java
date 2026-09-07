#!/usr/bin/env python3
"""Freeze Arena Trap target eligibility and final state from the pinned Python oracle."""
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

    def spec(name: str, *, types=None, ability=None, movement=None) -> PokemonSpec:
        abilities = [{"name": ability}] if ability else []
        return PokemonSpec(
            species=name,
            level=20,
            types=list(types or ["Normal"]),
            hp_stat=10,
            atk=12,
            defense=10,
            spatk=12,
            spdef=10,
            spd=10,
            moves=[],
            abilities=abilities,
            movement=dict(movement or {"overland": 4}),
        )

    trainers = {
        "a": TrainerState(identifier="a", name="A", team="players"),
        "b": TrainerState(identifier="b", name="B", team="foes"),
    }
    pokemon = {
        "holder": PokemonState(spec=spec("Holder", ability="Arena Trap"), controller_id="a", position=(2, 2), active=True),
        "normal": PokemonState(spec=spec("Normal"), controller_id="b", position=(2, 3), active=True),
        "far": PokemonState(spec=spec("Far"), controller_id="b", position=(9, 9), active=True),
        "flying": PokemonState(spec=spec("Flying", types=["Flying"]), controller_id="b", position=(2, 4), active=True),
        "levitate": PokemonState(spec=spec("Levitate", ability="Levitate"), controller_id="b", position=(2, 5), active=True),
        "sky4": PokemonState(spec=spec("Sky4", movement={"overland": 4, "sky": 4}), controller_id="b", position=(3, 2), active=True),
        "burrow4": PokemonState(spec=spec("Burrow4", movement={"overland": 4, "burrow": 4}), controller_id="b", position=(3, 3), active=True),
        "inactive": PokemonState(spec=spec("Inactive"), controller_id="b", position=None, active=False),
        "ally": PokemonState(spec=spec("Ally"), controller_id="a", position=(2, 1), active=True),
    }
    battle = BattleState(trainers=trainers, pokemon=pokemon, grid=GridState(width=12, height=12))
    battle.round = 1
    battle._apply_arena_trap()

    slowed = [pid for pid, mon in battle.pokemon.items() if mon.has_status("Slowed")]
    events = [
        event for event in battle.log
        if event.get("type") == "ability" and event.get("ability") == "Arena Trap"
    ]
    event_rows = [
        "|".join([
            str(event.get("actor") or ""),
            str(event.get("target") or ""),
            str(event.get("ability") or ""),
            str(event.get("effect") or ""),
            str(event.get("description") or ""),
        ])
        for event in events
    ]
    status_rows = []
    for pid in slowed:
        status = next(
            entry for entry in battle.pokemon[pid].statuses
            if str(entry.get("name") or "").strip().lower() == "slowed"
        )
        status_rows.append("|".join([
            pid,
            str(status.get("remaining") or ""),
            str(status.get("source") or ""),
            str(status.get("source_id") or ""),
        ]))

    rows = [
        "SLOWED\t" + ",".join(slowed),
        "EVENTS\t" + ";".join(event_rows),
        "STATUSES\t" + ";".join(status_rows),
    ]
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
