#!/usr/bin/env python3
"""Freeze baseline Intimidate stage mutation and ordered semantic events from Python."""
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
        "holder": PokemonState(spec=spec("Holder", "Intimidate"), controller_id="a", position=(2, 2), active=True),
        "near-first": PokemonState(spec=spec("NearFirst"), controller_id="b", position=(2, 3), active=True),
        "near-second": PokemonState(spec=spec("NearSecond"), controller_id="b", position=(3, 3), active=True),
        "far": PokemonState(spec=spec("Far"), controller_id="b", position=(4, 2), active=True),
    }
    battle = BattleState(trainers=trainers, pokemon=pokemon, grid=GridState(width=8, height=8))
    battle.round = 3
    pokemon["holder"].add_temporary_effect("joined_round", round=battle.round)

    battle._trigger_intimidate("holder")

    rows: list[str] = []
    used = any(
        int(entry.get("round", 0) or 0) == battle.round
        for entry in pokemon["holder"].get_temporary_effects("intimidate_used")
    )
    rows.append("\t".join(["USED", "true" if used else "false"]))
    for target_id in ("near-first", "near-second", "far"):
        rows.append("\t".join([
            "STAGE",
            target_id,
            str(int(pokemon[target_id].combat_stages.get("atk", 0) or 0)),
        ]))

    for event in battle.log:
        if event.get("type") == "combat_stage" and event.get("move") == "Intimidate":
            rows.append("\t".join([
                "EVENT",
                "combat_stage",
                str(event.get("actor") or ""),
                str(event.get("target") or ""),
                str(event.get("stat") or ""),
                str(event.get("effect") or ""),
                str(int(event.get("amount", 0) or 0)),
                str(int(event.get("new_stage", 0) or 0)),
                str(event.get("description") or ""),
            ]))
        elif event.get("type") == "ability" and event.get("ability") == "Intimidate":
            rows.append("\t".join([
                "EVENT",
                "ability",
                str(event.get("actor") or ""),
                str(event.get("target") or ""),
                str(event.get("effect") or ""),
                str(event.get("move") or ""),
                str(int(event.get("target_hp", 0) or 0)),
                str(int(event.get("round", 0) or 0)),
                str(event.get("phase") or ""),
            ]))

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
