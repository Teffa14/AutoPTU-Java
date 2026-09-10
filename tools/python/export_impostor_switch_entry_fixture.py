#!/usr/bin/env python3
"""Freeze pinned Python Impostor behavior when a combatant enters via switch."""
from __future__ import annotations

import argparse
import random
import sys
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.data_models import MoveSpec, PokemonSpec
    from auto_ptu.rules import BattleState, GridState, PokemonState, TrainerState

    def spec(name: str, abilities) -> PokemonSpec:
        names = abilities if isinstance(abilities, list) else [abilities]
        move = MoveSpec(
            name="Tackle", type="Normal", category="Physical", db=6, ac=2,
            range_kind="Melee", range_text="Melee, 1 Target"
        )
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
            abilities=[{"name": ability} for ability in names if ability],
            items=[],
            movement={"overland": 4},
        )

    trainers = {
        "a": TrainerState(identifier="a", name="A", team="players"),
        "b": TrainerState(identifier="b", name="B", team="foes"),
    }
    outgoing = PokemonState(spec=spec("Outgoing", []), controller_id="a", position=(2, 2), active=True)
    replacement = PokemonState(spec=spec("Imposter", ["Imposter"]), controller_id="a", position=None, active=False)
    target = PokemonState(spec=spec("Target", ["Pressure"]), controller_id="b", position=(2, 3), active=True)
    target.combat_stages["atk"] = 2
    target.combat_stages["def"] = -1
    effective_before = replacement.ability_names()

    battle = BattleState(
        trainers=trainers,
        pokemon={"a-1": outgoing, "a-2": replacement, "b-1": target},
        grid=GridState(width=10, height=10),
    )
    battle.rng = random.Random(42)
    battle.round = 1

    battle._apply_switch(
        outgoing_id="a-1",
        replacement_id="a-2",
        initiator_id="a",
        allow_replacement_turn=False,
        allow_immediate=False,
    )

    events = [
        event for event in battle.log
        if event.get("type") == "ability"
        and event.get("ability") == "Impostor"
        and event.get("actor") == "a-2"
        and event.get("effect") == "transform"
    ]
    entrained = replacement.get_temporary_effects("entrained_ability")
    joined = replacement.get_temporary_effects("joined_round")
    used = replacement.get_temporary_effects("impostor_used")

    rows = [
        "EFFECTIVE_BEFORE\t" + ",".join(effective_before),
        "EFFECTIVE_AFTER\t" + ",".join(replacement.ability_names()),
        "POSITION\t" + ("" if replacement.position is None else f"{replacement.position[0]},{replacement.position[1]}"),
        "ATK_STAGE\t" + str(replacement.combat_stages.get("atk", 0)),
        "DEF_STAGE\t" + str(replacement.combat_stages.get("def", 0)),
        "ENTRAINED_COUNT\t" + str(len(entrained)),
        "ENTRAINED_ABILITY\t" + ("" if not entrained else str(entrained[0].get("ability") or "")),
        "JOINED_ROUND_COUNT\t" + str(sum(1 for entry in joined if int(entry.get("round", 0) or 0) == battle.round)),
        "USED_COUNT\t" + str(sum(1 for entry in used if int(entry.get("round", 0) or 0) == battle.round)),
        "EVENT_COUNT\t" + str(len(events)),
        "EVENT_TARGET\t" + ("" if not events else str(events[0].get("target") or "")),
        "EVENT_PHASE\t" + ("" if not events else str(events[0].get("phase") or "")),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
