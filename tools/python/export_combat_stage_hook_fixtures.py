#!/usr/bin/env python3
"""Freeze combat-stage hook behavior from the pinned Python oracle."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from types import SimpleNamespace


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    sys.path.insert(0, str(source_root))

    hooks_source = (source_root / "auto_ptu" / "rules" / "hooks" / "combat_stage_hooks.py").read_text(encoding="utf-8")
    reactions_source = (source_root / "auto_ptu" / "rules" / "hooks" / "abilities" / "combat_stage_reactions.py").read_text(encoding="utf-8")
    contracts = {
        "registry_is_phase_scoped": int('_COMBAT_STAGE_HOOKS.get(phase, [])' in hooks_source),
        "registration_preserves_order": int('_COMBAT_STAGE_HOOKS.setdefault(phase, []).append(func)' in hooks_source),
        "dispatch_preserves_order": int('for func in hooks:' in hooks_source),
        "dispatch_returns_new_events_only": int('return ctx.events[start:]' in hooks_source),
        "minus_uses_radius_10": int('_ability_in_radius(ctx.target.position, "Minus [SwSh]", 10)' in reactions_source),
        "plus_uses_radius_10": int('_ability_in_radius(ctx.target.position, "Plus [SwSh]", 10)' in reactions_source),
        "minus_reentry_is_suppressed": int('skip_minus_swsh=True' in reactions_source),
        "plus_reentry_is_suppressed": int('skip_plus_swsh=True' in reactions_source),
        "defiant_reenters": int('defiant_bonus = 2 + abs(ctx.applied_delta)' in reactions_source and 'effect="defiant"' in reactions_source),
        "competitive_reenters": int('effect="competitive"' in reactions_source and 'delta=2' in reactions_source),
    }
    if not all(contracts.values()):
        raise RuntimeError(f"pinned oracle combat-stage hook contract changed: {contracts}")

    from auto_ptu.rules.hooks.abilities.combat_stage_reactions import (
        _competitive,
        _defiant,
        _minus_swsh,
        _plus_swsh,
        _simple_doubles_stage_changes,
    )
    from auto_ptu.rules.hooks.combat_stage_hooks import CombatStageHookContext

    class Target:
        def __init__(self, stage: int = 0, abilities=()) -> None:
            self.combat_stages = {"atk": stage}
            self.hp = 20
            self.position = (0, 0)
            self._abilities = set(abilities)
        def has_ability(self, name: str) -> bool:
            return name in self._abilities

    rows = []
    simple_scenarios = [
        ("simple_raise", 1, 1, True), ("simple_drop", -1, -1, True),
        ("simple_raise_two", 1, 2, True), ("simple_upper_clamp", 6, 1, True),
        ("simple_lower_clamp", -6, -1, True), ("zero_delta", 0, 0, True), ("no_simple", 1, 1, False),
    ]
    for scenario, start_stage, applied_delta, has_simple in simple_scenarios:
        target = Target(start_stage, ("Simple",) if has_simple else ())
        events = []
        ctx = SimpleNamespace(events=events, target=target, stat="atk", applied_delta=applied_delta,
                              target_id="target", move=SimpleNamespace(name="Test Move"))
        _simple_doubles_stage_changes(ctx)
        event = events[0] if events else {}
        rows.append((scenario, start_stage, applied_delta, int(has_simple), int(target.combat_stages["atk"]),
                     len(events), str(event.get("effect") or ""), int(event.get("amount") or 0),
                     "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""))

    class BattleRecorder:
        def __init__(self, *, holders=(), teams=None):
            self.calls = []
            self.holders = list(holders)
            self.teams = dict(teams or {})
            self.radius_queries = []
        def _apply_combat_stage(self, events, **kwargs): self.calls.append(kwargs)
        def _ability_in_radius(self, position, ability, radius, **kwargs):
            self.radius_queries.append((position, ability, radius, kwargs.get("team")))
            return list(self.holders)
        def _team_for(self, pid): return self.teams.get(pid)

    reaction_scenarios = [
        ("defiant_external_drop", "defiant", "actor", "target", "Tail Whip", "def", -1, ("Defiant",)),
        ("defiant_self_drop", "defiant", "target", "target", "Close Combat", "def", -1, ("Defiant",)),
        ("defiant_synthetic_guard", "defiant", "actor", "target", "Defiant", "def", -1, ("Defiant",)),
        ("defiant_absent", "defiant", "actor", "target", "Tail Whip", "def", -1, ()),
        ("competitive_external_drop", "competitive", "actor", "target", "Tail Whip", "def", -1, ("Competitive",)),
        ("competitive_spatk_drop", "competitive", "actor", "target", "Fake Tears", "spatk", -1, ("Competitive",)),
        ("competitive_self_drop", "competitive", "target", "target", "Overheat", "spatk", -1, ("Competitive",)),
        ("competitive_synthetic_guard", "competitive", "actor", "target", "Competitive", "def", -1, ("Competitive",)),
        ("competitive_absent", "competitive", "actor", "target", "Tail Whip", "def", -1, ()),
        ("positive_change_no_reaction", "defiant", "actor", "target", "Howl", "atk", 1, ("Defiant",)),
    ]
    for scenario, reaction, attacker_id, target_id, move_name, stat, applied_delta, abilities in reaction_scenarios:
        battle = BattleRecorder(); events = []; target = Target(0, abilities)
        ctx = CombatStageHookContext(battle, events, attacker_id, target_id, SimpleNamespace(name=move_name), target,
                                     stat, applied_delta, applied_delta, "fixture", "fixture", None, False, False)
        (_defiant if reaction == "defiant" else _competitive)(ctx)
        call = battle.calls[0] if battle.calls else {}; synthetic = call.get("move")
        rows.append((scenario, 0, applied_delta, 0, 0, len(events), "", 0, reaction, attacker_id, target_id,
                     move_name, ",".join(abilities), len(battle.calls), str(getattr(synthetic, "name", "") or ""),
                     str(call.get("stat") or ""), int(call.get("delta") or 0), "", "", "", 0, 0, "", ""))

    spatial_scenarios = [
        # scenario, reaction, attacker, delta, holders, teams, skip_minus, skip_plus
        ("minus_external_enemy", "minus", "actor", -1, ("holder",), {"actor":"enemy","target":"ally","holder":"enemy"}, False, False),
        ("minus_same_team_holder", "minus", "actor", -1, ("holder",), {"actor":"enemy","target":"ally","holder":"ally"}, False, False),
        ("minus_self_drop", "minus", "target", -1, ("holder",), {"target":"ally","holder":"enemy"}, False, False),
        ("minus_skip_guard", "minus", "actor", -1, ("holder",), {"actor":"enemy","target":"ally","holder":"enemy"}, True, False),
        ("minus_positive_change", "minus", "actor", 1, ("holder",), {"actor":"enemy","target":"ally","holder":"enemy"}, False, False),
        ("plus_ally_raise", "plus", "actor", 1, ("holder",), {"actor":"ally","target":"ally","holder":"ally"}, False, False),
        ("plus_enemy_holder", "plus", "actor", 1, ("holder",), {"actor":"ally","target":"ally","holder":"enemy"}, False, False),
        ("plus_target_holder_excluded", "plus", "actor", 1, ("target",), {"actor":"ally","target":"ally"}, False, False),
        ("plus_skip_guard", "plus", "actor", 1, ("holder",), {"actor":"ally","target":"ally","holder":"ally"}, False, True),
        ("plus_negative_change", "plus", "actor", -1, ("holder",), {"actor":"ally","target":"ally","holder":"ally"}, False, False),
        ("plus_first_holder_wins", "plus", "actor", 1, ("holder","holder2"), {"actor":"ally","target":"ally","holder":"ally","holder2":"ally"}, False, False),
    ]
    for scenario, reaction, attacker_id, applied_delta, holders, teams, skip_minus, skip_plus in spatial_scenarios:
        battle = BattleRecorder(holders=holders, teams=teams); events = []; target = Target()
        ctx = CombatStageHookContext(
            battle, events, attacker_id, "target", SimpleNamespace(name="Test Move"), target,
            "def", applied_delta, applied_delta, "fixture", "fixture", None, skip_minus, skip_plus
        )
        (_minus_swsh if reaction == "minus" else _plus_swsh)(ctx)
        call = battle.calls[0] if battle.calls else {}; synthetic = call.get("move"); event = events[-1] if events else {}
        query = battle.radius_queries[0] if battle.radius_queries else (None, "", 0, None)
        selected_holder = str(event.get("actor") or "")
        rows.append((scenario, 0, applied_delta, 0, 0, len(events), str(event.get("effect") or ""), int(event.get("amount") or 0),
                     reaction, attacker_id, "target", "Test Move", "", len(battle.calls), str(getattr(synthetic, "name", "") or ""),
                     str(call.get("stat") or ""), int(call.get("delta") or 0), selected_holder, str(teams.get(selected_holder) or ""),
                     str(teams.get("target") or ""), int(query[2] or 0), int(skip_minus or skip_plus), str(query[1] or ""),
                     str(event.get("ability") or "")))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    header = (
        "scenario\tstart_stage\tapplied_delta\thas_simple\texpected_stage\tevent_count\tevent_effect\tevent_amount"
        "\treaction\tattacker_id\ttarget_id\tmove_name\tabilities\trecursive_call_count\trecursive_move"
        "\trecursive_stat\trecursive_delta\tholder_id\tholder_team\ttarget_team\tquery_radius\tskip_guard"
        "\tquery_ability\tevent_ability\n"
    )
    args.output.write_text(header + "".join("\t".join(map(str, row)) + "\n" for row in rows), encoding="utf-8")
    print(f"wrote {len(rows)} Python combat-stage hook fixtures to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
