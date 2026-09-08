#!/usr/bin/env python3
"""Freeze Intimidate interactions with target-owned combat-stage prevention abilities."""
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

    rows: list[str] = []
    for scenario, blocker in (
        ("clear_body", "Clear Body"),
        ("full_metal_body", "Full Metal Body"),
        ("white_smoke", "White Smoke"),
        ("hyper_cutter", "Hyper Cutter"),
    ):
        trainers = {
            "a": TrainerState(identifier="a", name="A", team="players"),
            "b": TrainerState(identifier="b", name="B", team="foes"),
        }
        pokemon = {
            "holder": PokemonState(spec=spec("Holder", "Intimidate"), controller_id="a", position=(2, 2), active=True),
            "target": PokemonState(spec=spec("Target", blocker), controller_id="b", position=(2, 3), active=True),
        }
        for mon in pokemon.values():
            mon.hp = 20
        battle = BattleState(trainers=trainers, pokemon=pokemon, grid=GridState(width=6, height=6))
        battle.round = 3
        pokemon["holder"].add_temporary_effect("joined_round", round=battle.round)

        battle._trigger_intimidate("holder")

        rows.append("\t".join([
            "SCENARIO", scenario, blocker,
            str(int(pokemon["target"].combat_stages.get("atk", 0) or 0)),
        ]))
        for event in battle.log:
            if event.get("effect") == "combat_stage_block":
                rows.append("\t".join([
                    "PREVENT", scenario,
                    str(event.get("ability") or ""),
                    str(event.get("actor") or ""),
                    str(event.get("target") or ""),
                    str(event.get("move") or ""),
                    str(event.get("effect") or ""),
                ]))
            elif event.get("type") == "ability" and event.get("ability") == "Intimidate":
                rows.append("\t".join([
                    "INTIMIDATE", scenario,
                    str(event.get("actor") or ""),
                    str(event.get("target") or ""),
                    str(event.get("effect") or ""),
                    str(event.get("move") or ""),
                ]))

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
