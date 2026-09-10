#!/usr/bin/env python3
"""Freeze pinned Python Impostor negative-guard behavior."""
from __future__ import annotations

import argparse
import random
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

    def spec(name: str, abilities, speed: int) -> PokemonSpec:
        names = abilities if isinstance(abilities, list) else [abilities]
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
            abilities=[{"name": ability} for ability in names if ability],
            movement={"overland": 4},
        )

    def pokemon(name: str, abilities, controller: str, position: tuple[int, int], speed: int) -> PokemonState:
        state = PokemonState(
            spec=spec(name, abilities, speed),
            controller_id=controller,
            position=position,
            active=True,
        )
        state.hp = 20
        return state

    def battle(entries, seed: int = 42) -> BattleState:
        result = BattleState(
            trainers={
                "players": TrainerState(identifier="players", name="Players", team="players"),
                "foes": TrainerState(identifier="foes", name="Foes", team="foes"),
            },
            pokemon=OrderedDict(entries),
            grid=GridState(width=6, height=6),
        )
        result.rng = random.Random(seed)
        return result

    def impostor_events(state: BattleState, actor: str) -> list[dict]:
        return [
            event for event in state.log
            if event.get("type") == "ability"
            and event.get("ability") == "Impostor"
            and event.get("actor") == actor
        ]

    def used_count(mon: PokemonState, round_number: int) -> int:
        return sum(
            1 for entry in mon.get_temporary_effects("impostor_used")
            if int(entry.get("round", 0) or 0) == round_number
        )

    no_target_holder = pokemon("No Target", "Impostor", "players", (2, 2), 20)
    no_target = battle([("holder", no_target_holder)])
    PhaseController(no_target).start_round()

    no_ability_holder = pokemon("No Ability Holder", "Impostor", "players", (2, 2), 20)
    no_ability_target = pokemon("No Ability Target", [], "foes", (2, 3), 10)
    no_ability = battle([("holder", no_ability_holder), ("target", no_ability_target)])
    PhaseController(no_ability).start_round()
    no_ability_target_effective = no_ability_target.ability_names()
    no_ability_entrained = no_ability_holder.get_temporary_effects("entrained_ability")
    no_ability_assigned_is_none = bool(
        no_ability_entrained and no_ability_entrained[0].get("ability") is None
    )

    transformed_holder = pokemon("Transformed Holder", "Impostor", "players", (2, 2), 20)
    transformed_holder.add_temporary_effect("entrained_ability", ability="Pressure", source="Transform")
    transformed_target = pokemon("Transformed Target", "Levitate", "foes", (2, 3), 10)
    transformed = battle([("holder", transformed_holder), ("target", transformed_target)])
    PhaseController(transformed).start_round()
    transformed_effective = transformed_holder.ability_names()

    repeat_holder = pokemon("Repeat Holder", "Impostor", "players", (2, 2), 20)
    repeat_target = pokemon("Repeat Target", ["Levitate", "Pressure", "Blaze"], "foes", (2, 3), 10)
    repeated = battle([("holder", repeat_holder), ("target", repeat_target)], seed=42)
    PhaseController(repeated).start_round()
    before_events = len(impostor_events(repeated, "holder"))
    before_rng = repeated.rng.getstate()
    repeated._trigger_impostor("holder")
    after_rng = repeated.rng.getstate()
    after_events = len(impostor_events(repeated, "holder"))

    rows = [
        "NO_TARGET_EVENT_COUNT\t" + str(len(impostor_events(no_target, "holder"))),
        "NO_TARGET_USED_COUNT\t" + str(used_count(no_target_holder, no_target.round)),
        "NO_TARGET_ENTRAINED_COUNT\t" + str(len(no_target_holder.get_temporary_effects("entrained_ability"))),
        "NO_ABILITY_TARGET_EFFECTIVE\t" + ",".join(no_ability_target_effective),
        "NO_ABILITY_ASSIGNED_IS_NONE\t" + ("1" if no_ability_assigned_is_none else "0"),
        "NO_ABILITY_EVENT_COUNT\t" + str(len(impostor_events(no_ability, "holder"))),
        "NO_ABILITY_USED_COUNT\t" + str(used_count(no_ability_holder, no_ability.round)),
        "NO_ABILITY_ENTRAINED_COUNT\t" + str(len(no_ability_holder.get_temporary_effects("entrained_ability"))),
        "TRANSFORMED_EVENT_COUNT\t" + str(len(impostor_events(transformed, "holder"))),
        "TRANSFORMED_USED_COUNT\t" + str(used_count(transformed_holder, transformed.round)),
        "TRANSFORMED_EFFECTIVE_ABILITIES\t" + ",".join(transformed_effective),
        "REPEAT_EVENT_DELTA\t" + str(after_events - before_events),
        "REPEAT_USED_COUNT\t" + str(used_count(repeat_holder, repeated.round)),
        "REPEAT_ENTRAINED_COUNT\t" + str(len(repeat_holder.get_temporary_effects("entrained_ability"))),
        "REPEAT_RNG_UNCHANGED\t" + ("1" if before_rng == after_rng else "0"),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
