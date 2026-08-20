#!/usr/bin/env python3
"""Export adjacent elemental aura behavior from the pinned Python AutoPTU oracle."""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.data_models import MoveSpec
    from auto_ptu.rules.hooks.ability_hooks import AbilityHookContext
    from auto_ptu.rules.hooks.abilities.aura_adjacent_bonuses import (
        _adjacent_aura_sources,
        _apply_adjacent_aura_bonus,
    )

    @dataclass
    class Spec:
        types: list[str]

    class Mon:
        def __init__(self, *, position, ability="", team="A", active=True, fainted=False):
            self.position = position
            self.ability = ability
            self.team = team
            self.active = active
            self.fainted = fainted
            self.hp = 30
            self.spec = Spec(["Normal"])
            self.injuries = 0

        def has_ability(self, name):
            return self.ability.strip().lower() == str(name).strip().lower()

    class Battle:
        def __init__(self, pokemon):
            self.pokemon = pokemon

        def _team_for(self, pid):
            return self.pokemon[pid].team

        def _ability_in_radius(self, *args, **kwargs):
            return []

        def _aura_break_blockers(self, *args, **kwargs):
            return []

    # The Java post-damage contract only accepts damaging MoveCombatProfile values.
    # Python's Status guard remains upstream of this seam and is not weakened here.
    cases = [
        ("water_adjacent", "Water", "Special", "Aqua Boost", "A", (1, 2), True, False, None),
        ("fire_adjacent", "Fire", "Physical", "Ignition Boost", "A", (2, 1), True, False, None),
        ("electric_adjacent", "Electric", "Special", "Thunder Boost", "A", (2, 2), True, False, None),
        ("wrong_type", "Grass", "Special", "Aqua Boost", "A", (1, 2), True, False, None),
        ("enemy_holder", "Water", "Special", "Aqua Boost", "B", (1, 2), True, False, None),
        ("too_far", "Water", "Special", "Aqua Boost", "A", (3, 1), True, False, None),
        ("inactive_holder", "Water", "Special", "Aqua Boost", "A", (1, 2), False, False, None),
        ("fainted_holder", "Water", "Special", "Aqua Boost", "A", (1, 2), True, True, None),
        ("first_source_wins", "Water", "Special", "Aqua Boost", "A", (1, 2), True, False, "ally-1"),
    ]

    rows = ["name\tmove_type\tcategory\tability\tholder_team\tholder_x\tholder_y\tactive\tfainted\texpected_source\texpected_bonus\texpected_events"]
    for name, move_type, category, ability, holder_team, holder_pos, active, fainted, forced_source in cases:
        attacker = Mon(position=(1, 1), team="A")
        defender = Mon(position=(1, 0), team="B")
        holder = Mon(position=holder_pos, ability=ability, team=holder_team, active=active, fainted=fainted)
        pokemon = {"actor": attacker, "target": defender, "ally-1": holder}
        if name == "first_source_wins":
            pokemon["ally-2"] = Mon(position=(2, 1), ability=ability, team="A")
        battle = Battle(pokemon)
        move = MoveSpec(name="Oracle Move", type=move_type, category=category, db=6, ac=2)
        result = {"hit": True, "damage": 20}
        events = []
        ctx = AbilityHookContext(
            battle=battle,
            attacker_id="actor",
            attacker=attacker,
            defender_id="target",
            defender=defender,
            move=move,
            effective_move=move,
            events=events,
            phase="post_result_auras",
            result=result,
        )
        _adjacent_aura_sources(ctx)
        _apply_adjacent_aura_bonus(ctx)
        source = (
            result.get("aqua_boosted_by")
            or result.get("ignition_boosted_by")
            or result.get("thunder_boosted_by")
            or ""
        )
        bonus = int(result.get("damage", 0)) - 20
        if forced_source is not None and source != forced_source:
            raise AssertionError(f"{name}: expected source {forced_source}, got {source}")
        rows.append("\t".join(map(str, [
            name, move_type, category, ability, holder_team,
            holder_pos[0], holder_pos[1], active, fainted, source, bonus, len(events)
        ])))

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
