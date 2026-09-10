#!/usr/bin/env python3
"""Freeze the pinned Python _apply_switch() entry-state prefix."""
from __future__ import annotations

import argparse
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

    def spec(name: str) -> PokemonSpec:
        move = MoveSpec(
            name="Tackle", type="Normal", category="Physical", db=6, ac=2,
            range_kind="Melee", range_text="Melee, 1 Target"
        )
        return PokemonSpec(
            species=name, level=20, types=["Normal"], hp_stat=10,
            atk=10, defense=10, spatk=10, spdef=10, spd=10,
            moves=[move], abilities=[], items=[], movement={"overland": 4},
        )

    trainers = {
        "a": TrainerState(identifier="a", name="A", team="players"),
        "b": TrainerState(identifier="b", name="B", team="foes"),
    }
    outgoing = PokemonState(spec=spec("Outgoing"), controller_id="a", position=(4, 3), active=True)
    replacement = PokemonState(spec=spec("Replacement"), controller_id="a", position=None, active=False)
    target = PokemonState(spec=spec("Target"), controller_id="b", position=(4, 4), active=True)

    # Freeze Python's replacement cleanup, not only the empty-state happy path.
    replacement.add_temporary_effect("recalled", round=0)
    replacement.add_temporary_effect("released_from_ball", round=0)

    battle = BattleState(
        trainers=trainers,
        pokemon={"a-1": outgoing, "a-2": replacement, "b-1": target},
        grid=GridState(width=10, height=10),
    )
    battle.round = 3
    battle._apply_switch(
        outgoing_id="a-1",
        replacement_id="a-2",
        initiator_id="a",
        allow_replacement_turn=False,
        allow_immediate=False,
    )

    recalled_out = outgoing.get_temporary_effects("recalled")
    recalled_in = replacement.get_temporary_effects("recalled")
    released = replacement.get_temporary_effects("released_from_ball")
    joined = replacement.get_temporary_effects("joined_round")

    rows = [
        "ROUND\t" + str(battle.round),
        "OUTGOING_ACTIVE\t" + ("1" if outgoing.active else "0"),
        "OUTGOING_POSITION_IS_NONE\t" + ("1" if outgoing.position is None else "0"),
        "REPLACEMENT_ACTIVE\t" + ("1" if replacement.active else "0"),
        "POSITION\t" + ("" if replacement.position is None else f"{replacement.position[0]},{replacement.position[1]}"),
        "OUTGOING_RECALLED_COUNT\t" + str(len(recalled_out)),
        "OUTGOING_RECALLED_ROUND\t" + ("" if not recalled_out else str(recalled_out[-1].get("round", ""))),
        "REPLACEMENT_RECALLED_COUNT\t" + str(len(recalled_in)),
        "RELEASED_COUNT\t" + str(len(released)),
        "RELEASED_ROUND\t" + ("" if not released else str(released[-1].get("round", ""))),
        "JOINED_COUNT\t" + str(len(joined)),
        "JOINED_ROUND\t" + ("" if not joined else str(joined[-1].get("round", ""))),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
