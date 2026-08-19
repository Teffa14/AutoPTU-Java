#!/usr/bin/env python3
"""Export pinned Python Pink Pearl attacker-modifier behavior for Java parity tests."""
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
    from auto_ptu.rules.calculations import build_attack_context

    # Java MoveCombatProfile currently represents damaging moves only. Status-move
    # behavior remains outside this parity slice, so fixtures stay within the shared
    # Physical/Special contract instead of widening the Java move model implicitly.
    cases = [
        ("psychic_special_with_pearl", "Psychic", "Special", True),
        ("psychic_special_without_pearl", "Psychic", "Special", False),
        ("psychic_physical_with_pearl", "Psychic", "Physical", True),
        ("water_special_with_pearl", "Water", "Special", True),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(["name", "move_type", "category", "has_item", "flat_bonus", "effect_events"])
        for name, move_type, category, has_item in cases:
            flat_bonus, effect_events = evaluate_case(
                BattleState, PokemonState, TrainerState, PokemonSpec, MoveSpec,
                build_attack_context, move_type, category, has_item
            )
            writer.writerow([
                name,
                move_type,
                category,
                str(has_item).lower(),
                flat_bonus,
                effect_events,
            ])


def evaluate_case(
        BattleState,
        PokemonState,
        TrainerState,
        PokemonSpec,
        MoveSpec,
        build_attack_context,
        move_type: str,
        category: str,
        has_item: bool,
) -> tuple[int, int]:
    trainer = TrainerState(identifier="ash", name="Ash")
    attacker_spec = pokemon_spec(PokemonSpec, MoveSpec)
    attacker_spec.items = [{"name": "Pink Pearl"}] if has_item else []
    defender_spec = pokemon_spec(PokemonSpec, MoveSpec)
    move = MoveSpec(name="Oracle Move", type=move_type, category=category, db=4, ac=2, freq="EOT")
    battle = BattleState(
        trainers={trainer.identifier: trainer},
        pokemon={
            "ash-1": PokemonState(spec=attacker_spec, controller_id=trainer.identifier),
            "ash-2": PokemonState(spec=defender_spec, controller_id=trainer.identifier),
        },
    )
    attacker = battle.pokemon["ash-1"]
    defender = battle.pokemon["ash-2"]
    context = build_attack_context(attacker, defender, move)
    events = battle.item_system.apply_attacker_item_modifiers("ash-1", attacker, move, context)
    flat_bonus = sum(
        int(mod.value)
        for mod in context.modifiers
        if mod.kind == "damage_flat" and mod.slug == "item-pink-pearl-flat"
    )
    effect_events = sum(
        1
        for event in events
        if event.get("type") == "item"
        and event.get("item") == "Pink Pearl"
        and event.get("effect") == "damage_flat"
        and int(event.get("amount", 0) or 0) == 5
    )
    return flat_bonus, effect_events


def pokemon_spec(PokemonSpec, MoveSpec):
    return PokemonSpec(
        species="Eevee",
        level=20,
        types=["Normal"],
        hp_stat=10,
        atk=10,
        defense=10,
        spatk=10,
        spdef=10,
        spd=12,
        moves=[MoveSpec(name="Tackle", type="Normal", category="Physical", db=4, ac=2, freq="EOT")],
    )


if __name__ == "__main__":
    main()
