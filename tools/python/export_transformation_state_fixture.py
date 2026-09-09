#!/usr/bin/env python3
"""Freeze pinned Python Transform/Impostor state-copy, target-selection, and RNG behavior."""
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

    def spec(name: str, ability, speed: int) -> PokemonSpec:
        ability_names = ability if isinstance(ability, list) else [ability]
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
            abilities=[{"name": ability_name} for ability_name in ability_names],
            movement={"overland": 4},
        )

    def pokemon(name: str, ability, controller: str, position: tuple[int, int], speed: int = 10) -> PokemonState:
        state = PokemonState(
            spec=spec(name, ability, speed),
            controller_id=controller,
            position=position,
            active=True,
        )
        state.hp = 20
        return state

    copied_stage_values = {
        "atk": 2,
        "def": -1,
        "spatk": 3,
        "spdef": -2,
        "spd": 1,
        "accuracy": 2,
        "evasion": -1,
    }

    holder = pokemon("Holder", "Impostor", "players", (2, 2), 20)
    target = pokemon("Target", "Levitate", "foes", (2, 3), 10)

    holder.combat_stages.update({
        "atk": -4,
        "def": 4,
        "spatk": -4,
        "spdef": 4,
        "spd": -4,
        "accuracy": -3,
        "evasion": 3,
    })
    target.combat_stages.update(copied_stage_values)

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

    # Separate scenario freezes candidate filtering and the Python sort key. target-b is
    # inserted first, but target-a must win the equal Chebyshev-distance tie by combatant id.
    selector = pokemon("Selector", "Impostor", "players", (2, 2), 20)
    target_b = pokemon("Target B", "Blaze", "foes", (3, 2))
    target_a = pokemon("Target A", "Levitate", "foes", (2, 3))
    target_far = pokemon("Target Far", "Overgrow", "foes", (5, 5))
    target_unconscious = pokemon("Target Down", "Torrent", "foes", (2, 2))
    target_unconscious.hp = 0
    ally = pokemon("Ally", "Pressure", "players", (2, 1))

    selection_battle = BattleState(
        trainers={
            "players": TrainerState(identifier="players", name="Players", team="players"),
            "foes": TrainerState(identifier="foes", name="Foes", team="foes"),
        },
        pokemon=OrderedDict([
            ("selector", selector),
            ("target-b", target_b),
            ("target-a", target_a),
            ("target-far", target_far),
            ("target-down", target_unconscious),
            ("ally", ally),
        ]),
        grid=GridState(width=7, height=7),
    )
    PhaseController(selection_battle).start_round()
    selection_event = next(
        event for event in selection_battle.log
        if event.get("type") == "ability"
        and event.get("ability") == "Impostor"
        and event.get("actor") == "selector"
    )

    # A third scenario freezes random.choice(target_abilities), transformation state, and stream
    # consumption in one real start_round() execution. Speeds differ, so initiative does not
    # consume RNG before Impostor chooses the copied ability.
    rng_seed = 42
    rng_holder = pokemon("RNG Holder", "Impostor", "players", (2, 2), 20)
    rng_target_abilities = ["Levitate", "Pressure", "Blaze"]
    rng_target = pokemon("RNG Target", rng_target_abilities, "foes", (2, 3), 10)
    rng_target.combat_stages.update(copied_stage_values)
    rng_battle = BattleState(
        trainers={
            "players": TrainerState(identifier="players", name="Players", team="players"),
            "foes": TrainerState(identifier="foes", name="Foes", team="foes"),
        },
        pokemon=OrderedDict([("rng-holder", rng_holder), ("rng-target", rng_target)]),
        grid=GridState(width=6, height=6),
    )
    rng_battle.rng = random.Random(rng_seed)
    PhaseController(rng_battle).start_round()
    rng_event = next(
        event for event in rng_battle.log
        if event.get("type") == "ability"
        and event.get("ability") == "Impostor"
        and event.get("actor") == "rng-holder"
    )
    rng_next_random = rng_battle.rng.random()

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
        "SELECTION_ACTOR\tselector",
        "SELECTION_TARGET\t" + str(selection_event.get("target") or ""),
        "SELECTION_DISTANCE\t1",
        "SELECTION_CANDIDATES\ttarget-b,target-a,target-far,target-down,ally",
        "RNG_SEED\t" + str(rng_seed),
        "RNG_TARGET_ABILITIES\t" + ",".join(rng_target_abilities),
        "RNG_COPIED_STAGES\t" + ("1" if bool(rng_event.get("copied_stages")) else "0"),
        "RNG_ABILITY_ASSIGNED\t" + str(rng_event.get("ability_assigned") or ""),
        "RNG_NEXT_RANDOM\t" + repr(rng_next_random),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
