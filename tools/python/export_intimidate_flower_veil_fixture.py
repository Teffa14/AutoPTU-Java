#!/usr/bin/env python3
"""Freeze Intimidate interactions with Flower Veil spatial prevention."""
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

    def spec(name: str, *, ability: str | None = None, types: list[str] | None = None) -> PokemonSpec:
        return PokemonSpec(
            species=name,
            level=20,
            types=types or ["Normal"],
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

    scenarios = (
        ("standard_inside", "Flower Veil", (2, 10), ["Grass"]),
        ("standard_outside", "Flower Veil", (2, 14), ["Grass"]),
        ("errata_inside", "Flower Veil [Errata]", (2, 8), ["Grass"]),
        ("errata_outside", "Flower Veil [Errata]", (2, 9), ["Grass"]),
        ("standard_non_grass", "Flower Veil", (2, 4), ["Normal"]),
    )

    rows: list[str] = []
    for scenario, veil_ability, veil_position, target_types in scenarios:
        trainers = {
            "a": TrainerState(identifier="a", name="A", team="players"),
            "b": TrainerState(identifier="b", name="B", team="foes"),
        }
        pokemon = {
            "holder": PokemonState(
                spec=spec("Holder", ability="Intimidate"),
                controller_id="a",
                position=(2, 2),
                active=True,
            ),
            "target": PokemonState(
                spec=spec("Target", types=target_types),
                controller_id="b",
                position=(2, 3),
                active=True,
            ),
            "veil": PokemonState(
                spec=spec("Veil", ability=veil_ability),
                controller_id="b",
                position=veil_position,
                active=True,
            ),
        }
        for mon in pokemon.values():
            mon.hp = 20
        battle = BattleState(
            trainers=trainers,
            pokemon=pokemon,
            grid=GridState(width=20, height=20),
        )
        battle.round = 3
        pokemon["holder"].add_temporary_effect("joined_round", round=battle.round)

        battle._trigger_intimidate("holder")

        rows.append("\t".join([
            "SCENARIO",
            scenario,
            veil_ability,
            str(veil_position[0]),
            str(veil_position[1]),
            target_types[0],
            str(int(pokemon["target"].combat_stages.get("atk", 0) or 0)),
        ]))
        for event in battle.log:
            if event.get("effect") == "combat_stage_block":
                rows.append("\t".join([
                    "PREVENT",
                    scenario,
                    str(event.get("ability") or ""),
                    str(event.get("actor") or ""),
                    str(event.get("target") or ""),
                    str(event.get("move") or ""),
                    str(event.get("effect") or ""),
                ]))
            elif event.get("type") == "combat_stage" and event.get("target") == "target":
                rows.append("\t".join([
                    "STAGE",
                    scenario,
                    str(event.get("actor") or ""),
                    str(event.get("target") or ""),
                    str(event.get("move") or ""),
                    str(event.get("stat") or ""),
                    str(event.get("effect") or ""),
                    str(event.get("new_stage") or 0),
                ]))
            elif event.get("type") == "ability" and event.get("ability") == "Intimidate" and event.get("target") == "target":
                rows.append("\t".join([
                    "INTIMIDATE",
                    scenario,
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
