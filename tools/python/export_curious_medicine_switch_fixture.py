#!/usr/bin/env python3
"""Freeze Curious Medicine behavior from the pinned Python switch runtime."""
from __future__ import annotations

import argparse
import inspect
import random
import sys
import textwrap
from pathlib import Path


class SequenceRNG(random.Random):
    def __init__(self, values):
        super().__init__()
        self._values = list(values)

    def randint(self, a, b):
        if self._values:
            return self._values.pop(0)
        return b


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.data_models import MoveSpec, PokemonSpec
    from auto_ptu.rules import BattleState, GridState, PokemonState, TrainerState

    source = textwrap.dedent(inspect.getsource(BattleState._apply_switch))

    move = MoveSpec(
        name="Tackle",
        type="Normal",
        category="Physical",
        db=6,
        ac=2,
        range_kind="Melee",
        range_text="Melee, 1 Target",
    )

    def spec(name: str, *, ability: str | None = None) -> PokemonSpec:
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
            moves=[move],
            abilities=[{"name": ability}] if ability else [],
            movement={"overland": 4},
        )

    trainer_a = TrainerState(identifier="a", name="A", team="players")
    trainer_b = TrainerState(identifier="b", name="B", team="foes")

    outgoing = PokemonState(spec=spec("Outgoing"), controller_id="a", position=(2, 2), active=True)
    replacement = PokemonState(
        spec=spec("Curious", ability="Curious Medicine"),
        controller_id="a",
        position=None,
        active=False,
    )
    adjacent = PokemonState(spec=spec("Adjacent"), controller_id="a", position=(2, 3), active=True)
    range_two = PokemonState(spec=spec("RangeTwo"), controller_id="a", position=(2, 4), active=True)
    farther = PokemonState(spec=spec("Farther"), controller_id="a", position=(2, 5), active=True)
    enemy = PokemonState(spec=spec("Enemy"), controller_id="b", position=(3, 2), active=True)
    inactive_positioned = PokemonState(
        spec=spec("InactivePositioned"), controller_id="a", position=(1, 2), active=False
    )

    combatants = {
        "a-1": outgoing,
        "a-2": replacement,
        "a-3": adjacent,
        "a-4": range_two,
        "a-5": farther,
        "b-1": enemy,
        "a-6": inactive_positioned,
    }
    staged = {
        "a-2": replacement,
        "a-3": adjacent,
        "a-4": range_two,
        "a-5": farther,
        "b-1": enemy,
        "a-6": inactive_positioned,
    }
    for index, pokemon in enumerate(staged.values(), start=1):
        pokemon.combat_stages["atk"] = index
        pokemon.combat_stages["spd"] = -index

    battle = BattleState(
        trainers={"a": trainer_a, "b": trainer_b},
        pokemon=combatants,
        grid=GridState(width=10, height=10),
    )
    battle.rng = SequenceRNG([20] * 200)
    battle.round = 1

    before = {
        identifier: dict(pokemon.combat_stages)
        for identifier, pokemon in staged.items()
    }
    before_log_len = len(battle.log)
    battle._apply_switch(
        outgoing_id="a-1",
        replacement_id="a-2",
        initiator_id="a",
        allow_replacement_turn=False,
        allow_immediate=False,
    )
    new_events = battle.log[before_log_len:]
    curious_events = [
        event for event in new_events
        if event.get("ability") == "Curious Medicine"
    ]

    if range_two.combat_stages.get("atk") != 0:
        raise AssertionError("Pinned Curious Medicine contract no longer resets the range-two ally")
    if inactive_positioned.combat_stages.get("atk") != 0:
        raise AssertionError("Pinned Curious Medicine contract no longer resets positioned inactive allies")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        handle.write("SOURCE_BEGIN\n")
        for index, line in enumerate(source.splitlines(), start=1):
            handle.write(f"SOURCE\t{index}\t{line}\n")
        handle.write("SOURCE_END\n")
        handle.write(
            f"REPLACEMENT_POSITION\t{replacement.position[0]}\t{replacement.position[1]}\n"
        )
        for identifier, pokemon in staged.items():
            position = pokemon.position
            px = "" if position is None else str(position[0])
            py = "" if position is None else str(position[1])
            handle.write(
                "COMBATANT\t"
                f"{identifier}\t{pokemon.controller_id}\t{int(pokemon.active)}\t{px}\t{py}\t"
                f"{before[identifier].get('atk', 0)}\t{pokemon.combat_stages.get('atk', 0)}\t"
                f"{before[identifier].get('spd', 0)}\t{pokemon.combat_stages.get('spd', 0)}\n"
            )
        effects = getattr(replacement, "temporary_effects", [])
        for effect in effects:
            handle.write(f"REPLACEMENT_TEMP_EFFECT\t{effect!r}\n")
        handle.write(f"CURIOUS_MEDICINE_EVENT_COUNT\t{len(curious_events)}\n")
        for index, event in enumerate(curious_events):
            handle.write(f"CURIOUS_MEDICINE_EVENT\t{index}\t{repr(sorted(event.items()))}\n")
            handle.write(
                "CURIOUS_MEDICINE_EVENT_STRUCT\t"
                f"{index}\t{event.get('actor', '')}\t{event.get('target', '')}\t"
                f"{event.get('ability', '')}\t{event.get('move', '')}\t{event.get('effect', '')}\t"
                f"{event.get('description', '')}\t{event.get('target_hp', 0)}\t"
                f"{event.get('phase', '')}\t{event.get('round', 0)}\n"
            )
    print(output)


if __name__ == "__main__":
    main()
