#!/usr/bin/env python3
"""Export pinned Python Mega Launcher pre-damage behavior for Java parity tests."""
from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.data_models import MoveSpec, PokemonSpec
    from auto_ptu.rules import BattleState, PokemonState, TrainerState
    from auto_ptu.rules.hooks.ability_hooks import AbilityHookContext, apply_ability_hooks

    cases = [
        ("base_aura_sphere", "Mega Launcher", "Aura Sphere", 6),
        ("base_water_pulse_cap", "Mega Launcher", "Water Pulse", 19),
        ("base_unrelated", "Mega Launcher", "Tackle", 6),
        ("errata_dragon_pulse", "Mega Launcher [Errata]", "Dragon Pulse", 6),
        ("none_dark_pulse", "", "Dark Pulse", 6),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(["name", "ability", "move_name", "base_db", "effective_db", "event_amount"])
        for name, ability, move_name, base_db in cases:
            effective_db, event_amount = evaluate_case(
                BattleState, PokemonState, TrainerState, PokemonSpec, MoveSpec,
                AbilityHookContext, apply_ability_hooks,
                ability, move_name, base_db,
            )
            writer.writerow([name, ability, move_name, base_db, effective_db, event_amount])


def evaluate_case(
        BattleState,
        PokemonState,
        TrainerState,
        PokemonSpec,
        MoveSpec,
        AbilityHookContext,
        apply_ability_hooks,
        ability: str,
        move_name: str,
        base_db: int,
) -> tuple[int, int]:
    trainer_a = TrainerState(identifier="a", name="A")
    trainer_b = TrainerState(identifier="b", name="B")
    attacker = PokemonState(
        spec=pokemon_spec(PokemonSpec, MoveSpec, ability),
        controller_id="a",
    )
    defender = PokemonState(
        spec=pokemon_spec(PokemonSpec, MoveSpec, ""),
        controller_id="b",
    )
    battle = BattleState(
        trainers={"a": trainer_a, "b": trainer_b},
        pokemon={"a-1": attacker, "b-1": defender},
    )
    move = MoveSpec(
        name=move_name,
        type="Water",
        category="Special",
        db=base_db,
        ac=2,
        freq="EOT",
    )
    effective_move = MoveSpec(**move.__dict__)
    events: list[dict] = []
    ctx = AbilityHookContext(
        battle=battle,
        attacker_id="a-1",
        attacker=attacker,
        defender_id="b-1",
        defender=defender,
        move=move,
        effective_move=effective_move,
        events=events,
        phase="pre_damage",
        result={},
    )
    emitted = apply_ability_hooks("pre_damage", ctx)
    amount = sum(
        int(event.get("amount", 0) or 0)
        for event in emitted
        if event.get("type") == "ability"
        and str(event.get("ability") or "") == ability
        and event.get("effect") == "db_bonus"
    )
    return int(ctx.effective_move.db or 0), amount


def pokemon_spec(PokemonSpec, MoveSpec, ability: str):
    abilities = [{"name": ability}] if ability else []
    return PokemonSpec(
        species="Blastoise",
        level=20,
        types=["Water"],
        hp_stat=10,
        atk=10,
        defense=10,
        spatk=10,
        spdef=10,
        spd=12,
        moves=[MoveSpec(name="Water Pulse", type="Water", category="Special", db=6, ac=2, freq="EOT")],
        abilities=abilities,
    )


if __name__ == "__main__":
    main()
