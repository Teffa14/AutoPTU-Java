#!/usr/bin/env python3
"""Freeze Intimidate trigger gating and adjacent-target order from the pinned Python oracle."""
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

    def build() -> BattleState:
        trainers = {
            "a": TrainerState(identifier="a", name="A", team="players"),
            "b": TrainerState(identifier="b", name="B", team="foes"),
        }
        pokemon = {
            "holder": PokemonState(spec=spec("Holder", "Intimidate"), controller_id="a", position=(2, 2), active=True),
            "near-first": PokemonState(spec=spec("NearFirst"), controller_id="b", position=(2, 3), active=True),
            "near-second": PokemonState(spec=spec("NearSecond"), controller_id="b", position=(3, 3), active=True),
            "far": PokemonState(spec=spec("Far"), controller_id="b", position=(4, 2), active=True),
            "ally": PokemonState(spec=spec("Ally"), controller_id="a", position=(3, 2), active=True),
            "inactive": PokemonState(spec=spec("Inactive"), controller_id="b", position=None, active=False),
            "fainted": PokemonState(spec=spec("Fainted"), controller_id="b", position=(1, 2), active=True),
        }
        pokemon["fainted"].hp = 0
        battle = BattleState(trainers=trainers, pokemon=pokemon, grid=GridState(width=8, height=8))
        battle.round = 3
        return battle

    def run_case(name: str, *, joined: bool, already_used: bool, holder_active: bool = True, holder_hp: int | None = None) -> str:
        battle = build()
        holder = battle.pokemon["holder"]
        holder.active = holder_active
        if holder_hp is not None:
            holder.hp = holder_hp
        if joined:
            holder.add_temporary_effect("joined_round", round=battle.round)
        if already_used:
            holder.add_temporary_effect("intimidate_used", round=battle.round)
        before = len(holder.get_temporary_effects("intimidate_used"))
        battle._trigger_intimidate("holder")
        after = len(holder.get_temporary_effects("intimidate_used"))
        targets = [
            str(event.get("target") or "")
            for event in battle.log
            if event.get("type") == "ability"
            and event.get("ability") == "Intimidate"
            and event.get("effect") == "attack_drop"
        ]
        return "\t".join(["CASE", name, "true" if after > before else "false", ",".join(targets)])

    rows = [
        run_case("eligible", joined=True, already_used=False),
        run_case("not_joined", joined=False, already_used=False),
        run_case("already_used", joined=True, already_used=True),
        run_case("inactive_holder", joined=True, already_used=False, holder_active=False),
        run_case("fainted_holder", joined=True, already_used=False, holder_hp=0),
    ]
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
