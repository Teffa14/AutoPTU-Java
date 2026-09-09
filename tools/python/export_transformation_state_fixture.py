#!/usr/bin/env python3
"""Freeze the reusable state-copy portion of pinned Python Impostor behavior."""
from __future__ import annotations

import argparse
import sys
from collections import OrderedDict
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.data_models import PokemonSpec
    from auto_ptu.rules import BattleState, GridState, PokemonState, TrainerState
    from auto_ptu.rules.controllers.phase_controller import PhaseController

    def spec(name: str, ability: str, speed: int) -> PokemonSpec:
        return PokemonSpec(
            species=name,
            level=20,
            types=["Normal"],
            hp_stat=10,
            atk=12,
            defense=10,
            spatk=12,
            spdef=10,
            spd=speed,
            moves=[],
            abilities=[{"name": ability}],
            movement={"overland": 4},
        )

    holder = PokemonState(
        spec=spec("Holder", "Impostor", 20),
        controller_id="players",
        position=(2, 2),
        active=True,
    )
    target = PokemonState(
        spec=spec("Target", "Levitate", 10),
        controller_id="foes",
        position=(2, 3),
        active=True,
    )
    holder.hp = 20
    target.hp = 20

    holder.combat_stages.update({
        "atk": -4,
        "def": 4,
        "spatk": -4,
        "spdef": 4,
        "spd": -4,
        "accuracy": -3,
        "evasion": 3,
    })
    target.combat_stages.update({
        "atk": 2,
        "def": -1,
        "spatk": 3,
        "spdef": -2,
        "spd": 1,
        "accuracy": 2,
        "evasion": -1,
    })

    battle = BattleState(
        trainers={
            "players": TrainerState(identifier="players", name="Players", team="players"),
            "foes": TrainerState(identifier="foes", name="Foes", team="foes"),
        },
        pokemon=OrderedDict([("holder", holder), ("target", target)]),
        grid=GridState(width=6, height=6),
    )
    # start_round() itself marks active round-one combatants as joined. Do not preload an
    # entrained ability here: Python treats that copied ability as the effective ability,
    # which correctly makes the native Impostor trigger ineligible for another transform.
    PhaseController(battle).start_round()

    stage_order = ("atk", "def", "spatk", "spdef", "spd", "accuracy", "evasion")
    stage_snapshot = ",".join(f"{key}:{int(holder.combat_stages.get(key, 0) or 0)}" for key in stage_order)
    entrained = holder.get_temporary_effects("entrained_ability")
    if len(entrained) != 1:
        raise AssertionError(f"expected one copied entrained_ability, got {entrained!r}")
    assigned = entrained[0].get("ability")
    source = entrained[0].get("source")
    used = any(
        int(entry.get("round", 0) or 0) == battle.round
        for entry in holder.get_temporary_effects("impostor_used")
    )
    event = next(
        event for event in battle.log
        if event.get("type") == "ability" and event.get("ability") == "Impostor"
    )

    rows = [
        "COPIED_STAGES\t" + ("1" if bool(event.get("copied_stages")) else "0"),
        "ACTOR_STAGES\t" + stage_snapshot,
        "ABILITY_ASSIGNED\t" + str(assigned or ""),
        "ABILITY_SOURCE\t" + str(source or ""),
        "ENTRAINED_COUNT\t" + str(len(entrained)),
        "IMPOSTOR_USED\t" + ("1" if used else "0"),
        "EVENT\t" + "|".join([
            str(event.get("actor") or ""),
            str(event.get("target") or ""),
            str(event.get("ability") or ""),
            str(event.get("effect") or ""),
            "1" if bool(event.get("copied_stages")) else "0",
            str(event.get("ability_assigned") or ""),
        ]),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
