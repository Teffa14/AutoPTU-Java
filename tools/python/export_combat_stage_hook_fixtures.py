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
        "defiant_reenters": int('defiant_bonus = 2 + abs(ctx.applied_delta)' in reactions_source and 'effect="defiant"' in reactions_source),
        "competitive_reenters": int('effect="competitive"' in reactions_source and 'delta=2' in reactions_source),
    }
    if not all(contracts.values()):
        raise RuntimeError(f"pinned oracle combat-stage hook contract changed: {contracts}")

    from auto_ptu.rules.hooks.abilities.combat_stage_reactions import _competitive, _defiant, _simple_doubles_stage_changes
    from auto_ptu.rules.hooks.combat_stage_hooks import CombatStageHookContext

    class Target:
        def __init__(self, stage: int = 0, abilities=()) -> None:
            self.combat_stages = {"atk": stage}
            self.hp = 20
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
                     "", "", "", "", "", "", "", "", ""))

    class BattleRecorder:
        def __init__(self): self.calls = []
        def _apply_combat_stage(self, events, **kwargs): self.calls.append(kwargs)

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
                     str(call.get("stat") or ""), int(call.get("delta") or 0)))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    header = "scenario\tstart_stage\tapplied_delta\thas_simple\texpected_stage\tevent_count\tevent_effect\tevent_amount\treaction\tattacker_id\ttarget_id\tmove_name\tabilities\trecursive_call_count\trecursive_move\trecursive_stat\trecursive_delta\n"
    args.output.write_text(header + "".join("\t".join(map(str, row)) + "\n" for row in rows), encoding="utf-8")
    print(f"wrote {len(rows)} Python combat-stage hook fixtures to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
