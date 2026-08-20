#!/usr/bin/env python3
"""Export spatial post-damage aura behavior from the pinned Python AutoPTU oracle."""

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
    from auto_ptu.rules import BattleState
    from auto_ptu.rules.hooks.ability_hooks import AbilityHookContext
    from auto_ptu.rules.hooks.abilities.aura_adjacent_bonuses import (
        _adjacent_aura_sources,
        _apply_adjacent_aura_bonus,
    )
    from auto_ptu.rules.hooks.abilities.attacker_damage_bonuses import _aura_storm_errata_bonus

    @dataclass
    class Spec:
        types: list[str]

    class Mon:
        def __init__(
            self,
            *,
            position,
            ability="",
            team="A",
            active=True,
            fainted=False,
            types=None,
            injuries=0,
            temporary_effects=None,
        ):
            self.position = position
            self.ability = ability
            self.team = team
            self.active = active
            self.fainted = fainted
            self.hp = 30
            self.spec = Spec(list(types or ["Normal"]))
            self.injuries = injuries
            self._temporary_effects = list(temporary_effects or [])

        def ability_names(self):
            return [self.ability] if self.ability else []

        def has_ability(self, name):
            return self.ability.strip().lower() == str(name).strip().lower()

        def get_temporary_effects(self, name):
            target = str(name).strip().lower()
            return [
                dict(entry)
                for entry in self._temporary_effects
                if str(entry.get("name", "")).strip().lower() == target
            ]

        def remove_temporary_effect(self, name):
            target = str(name).strip().lower()
            self._temporary_effects = [
                entry
                for entry in self._temporary_effects
                if str(entry.get("name", "")).strip().lower() != target
            ]

    class Battle:
        def __init__(self, pokemon, *, aura_break_blocked=False):
            self.pokemon = pokemon
            self.round = 0
            self.aura_break_blocked = aura_break_blocked

        def _team_for(self, pid):
            return self.pokemon[pid].team

        def _ability_in_radius(self, position, ability_name, radius, team=None):
            matches = []
            for pid, mon in self.pokemon.items():
                if mon.fainted or not mon.active or mon.position is None:
                    continue
                if team is not None and self._team_for(pid) != team:
                    continue
                if not mon.has_ability(ability_name):
                    continue
                distance = max(abs(position[0] - mon.position[0]), abs(position[1] - mon.position[1]))
                if distance <= radius:
                    matches.append(pid)
            return matches

        def _aura_break_blockers(self, *args, **kwargs):
            return ["blocker"] if self.aura_break_blocked else []

    spatial_cases = [
        ("water_adjacent", "Water", "Special", "Aqua Boost", "A", (1, 2), True, False, ["Normal"], False, None),
        ("fire_adjacent", "Fire", "Physical", "Ignition Boost", "A", (2, 1), True, False, ["Normal"], False, None),
        ("electric_adjacent", "Electric", "Special", "Thunder Boost", "A", (2, 2), True, False, ["Normal"], False, None),
        ("wrong_type", "Grass", "Special", "Aqua Boost", "A", (1, 2), True, False, ["Normal"], False, None),
        ("enemy_holder", "Water", "Special", "Aqua Boost", "B", (1, 2), True, False, ["Normal"], False, None),
        ("too_far", "Water", "Special", "Aqua Boost", "A", (3, 1), True, False, ["Normal"], False, None),
        ("inactive_holder", "Water", "Special", "Aqua Boost", "A", (1, 2), False, False, ["Normal"], False, None),
        ("fainted_holder", "Water", "Special", "Aqua Boost", "A", (1, 2), True, True, ["Normal"], False, None),
        ("first_source_wins", "Water", "Special", "Aqua Boost", "A", (1, 2), True, False, ["Normal"], True, "ally-1"),
        ("power_spot_in_range", "Grass", "Special", "Power Spot", "A", (3, 1), True, False, ["Normal"], False, "ally-1"),
        ("power_spot_enemy", "Grass", "Special", "Power Spot", "B", (2, 1), True, False, ["Normal"], False, ""),
        ("power_spot_too_far", "Grass", "Special", "Power Spot", "A", (4, 1), True, False, ["Normal"], False, ""),
        ("type_aura_matching_primary", "Water", "Special", "Type Aura", "A", (4, 1), True, False, ["Water", "Ice"], False, "ally-1"),
        ("type_aura_secondary_only", "Water", "Special", "Type Aura", "A", (2, 1), True, False, ["Fire", "Water"], False, ""),
        ("type_aura_wrong_primary", "Water", "Special", "Type Aura", "A", (2, 1), True, False, ["Fire"], False, ""),
        ("type_aura_enemy", "Water", "Special", "Type Aura", "B", (2, 1), True, False, ["Water"], False, ""),
        ("type_aura_too_far", "Water", "Special", "Type Aura", "A", (5, 1), True, False, ["Water"], False, ""),
    ]

    rows = [
        "name\tmove_type\tcategory\tability\tholder_team\tholder_x\tholder_y\tactive\tfainted\t"
        "holder_types\texpected_source\texpected_bonus\texpected_events\tvariant\tactor_ability\t"
        "move_keywords\tactor_injuries\taura_break_blocked\taura_break_errata_inverts"
    ]

    for (
        name,
        move_type,
        category,
        ability,
        holder_team,
        holder_pos,
        active,
        fainted,
        holder_types,
        second_holder,
        forced_source,
    ) in spatial_cases:
        attacker = Mon(position=(1, 1), team="A")
        defender = Mon(position=(1, 0), team="B")
        holder = Mon(
            position=holder_pos,
            ability=ability,
            team=holder_team,
            active=active,
            fainted=fainted,
            types=holder_types,
        )
        pokemon = {"actor": attacker, "target": defender, "ally-1": holder}
        if second_holder:
            pokemon["ally-2"] = Mon(position=(2, 1), ability=ability, team="A", types=holder_types)
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
            or result.get("power_spot_source")
            or result.get("type_aura_source")
            or ""
        )
        bonus = int(result.get("damage", 0)) - 20
        if forced_source is not None and source != forced_source:
            raise AssertionError(f"{name}: expected source {forced_source}, got {source}")
        rows.append("\t".join(map(str, [
            name, move_type, category, ability, holder_team, holder_pos[0], holder_pos[1], active, fainted,
            "|".join(holder_types), source, bonus, len(events), "spatial", "", "", 0, False, False,
        ])))

    blocker_cases = [
        ("aura_break_enemy_active", "Aura Break", "B", True, False, False, "breaker-1", 1),
        ("aura_break_same_team", "Aura Break", "A", True, False, False, "", 0),
        ("aura_break_inactive_enemy", "Aura Break", "B", False, False, False, "", 0),
        ("aura_break_fainted_enemy", "Aura Break", "B", True, True, False, "", 0),
        ("aura_break_errata_is_not_base", "Aura Break [Errata]", "B", True, False, False, "", 0),
        ("aura_break_preserves_first_blocker", "Aura Break", "B", True, False, True, "breaker-1", 2),
    ]
    for name, ability, team, active, fainted, second_blocker, expected_source, expected_count in blocker_cases:
        pokemon = {
            "actor": Mon(position=(1, 1), team="A"),
            "breaker-1": Mon(
                position=(5, 5), ability=ability, team=team, active=active, fainted=fainted
            ),
        }
        if second_blocker:
            pokemon["breaker-2"] = Mon(position=(9, 9), ability="Aura Break", team="B")
        battle = Battle(pokemon)
        # Execute the real pinned BattleState implementation against the minimal state it reads.
        blockers = BattleState._aura_break_blockers(battle, "actor")
        source = blockers[0] if blockers else ""
        if source != expected_source or len(blockers) != expected_count:
            raise AssertionError(
                f"{name}: expected blockers ({expected_source!r}, {expected_count}), got {blockers!r}"
            )
        rows.append("\t".join(map(str, [
            name, "", "Special", ability, team, 5, 5, active, fainted, "Normal",
            source, 0, len(blockers), "blocker", "", "", 0, False, False,
        ])))

    aura_cases = [
        ("aura_storm_zero_injuries", "normal", "Aura Storm", ["Aura"], 0, False, False),
        ("aura_storm_two_injuries", "normal", "Aura Storm", ["Aura"], 2, False, False),
        ("aura_storm_missing_keyword", "normal", "Aura Storm", ["Contact"], 2, False, False),
        ("aura_storm_blocked", "normal", "Aura Storm", ["Aura"], 2, True, False),
        ("aura_storm_errata_zero_injuries", "errata", "Aura Storm [Errata]", [], 0, False, False),
        ("aura_storm_errata_two_injuries", "errata", "Aura Storm [Errata]", [], 2, False, False),
        ("aura_storm_errata_inverted", "errata", "Aura Storm [Errata]", [], 2, False, True),
    ]
    for name, variant, actor_ability, keywords, injuries, blocked, inverted in aura_cases:
        temporary_effects = []
        if inverted:
            temporary_effects.append({
                "name": "aura_break_errata",
                "ability": "Aura Storm [Errata]",
                "source_id": "breaker",
                "expires_round": 0,
            })
        attacker = Mon(
            position=(1, 1),
            team="A",
            ability=actor_ability,
            injuries=injuries,
            temporary_effects=temporary_effects,
        )
        defender = Mon(position=(1, 0), team="B")
        battle = Battle({"actor": attacker, "target": defender}, aura_break_blocked=blocked)
        move = MoveSpec(
            name="Oracle Aura Move",
            type="Psychic",
            category="Special",
            db=6,
            ac=2,
            keywords=keywords,
        )
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
            phase="post_result_auras" if variant == "normal" else "post_result",
            result=result,
        )
        if variant == "normal":
            _apply_adjacent_aura_bonus(ctx)
        else:
            _aura_storm_errata_bonus(ctx)
        bonus = int(result.get("damage", 0)) - 20
        expected_source = "actor" if any(event.get("ability", "").startswith("Aura Storm") for event in events) else ""
        rows.append("\t".join(map(str, [
            name, "Psychic", "Special", "", "A", 0, 0, True, False, "Normal",
            expected_source, bonus, len(events), variant, actor_ability, "|".join(keywords), injuries, blocked, inverted,
        ])))

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())