#!/usr/bin/env python3
"""Freeze Defiant/Competitive combat-stage reaction requests from pinned Python AutoPTU."""
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

    from auto_ptu.rules.hooks.combat_stage_hooks import CombatStageHookContext
    from auto_ptu.rules.hooks.abilities.combat_stage_reactions import _competitive, _defiant

    class Target:
        def __init__(self, abilities: tuple[str, ...]) -> None:
            self.hp = 20
            self._abilities = set(abilities)

        def has_ability(self, name: str) -> bool:
            return name in self._abilities

    class BattleRecorder:
        def __init__(self) -> None:
            self.calls: list[dict] = []

        def _apply_combat_stage(self, events, **kwargs) -> None:
            self.calls.append(kwargs)

    scenarios = [
        ("defiant_external_drop", "defiant", "actor", "target", "Tail Whip", "def", -1, ("Defiant",)),
        ("defiant_clamped_applied_drop", "defiant", "actor", "target", "Tail Whip", "def", -1, ("Defiant",)),
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

    rows = []
    for scenario, reaction, attacker_id, target_id, move_name, stat, applied_delta, abilities in scenarios:
        battle = BattleRecorder()
        events: list[dict] = []
        target = Target(abilities)
        ctx = CombatStageHookContext(
            battle=battle,
            events=events,
            attacker_id=attacker_id,
            target_id=target_id,
            move=SimpleNamespace(name=move_name),
            target=target,
            stat=stat,
            delta=applied_delta,
            applied_delta=applied_delta,
            effect="fixture",
            description="fixture",
            roll=None,
            skip_minus_swsh=False,
            skip_plus_swsh=False,
        )
        (_defiant if reaction == "defiant" else _competitive)(ctx)
        call = battle.calls[0] if battle.calls else {}
        synthetic_move = call.get("move")
        rows.append((
            scenario,
            reaction,
            attacker_id,
            target_id,
            move_name,
            stat,
            applied_delta,
            ",".join(abilities),
            len(battle.calls),
            str(call.get("attacker_id") or ""),
            str(call.get("target_id") or ""),
            str(getattr(synthetic_move, "name", "") or ""),
            str(call.get("stat") or ""),
            int(call.get("delta") or 0),
            str(call.get("effect") or ""),
            len(events),
        ))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    header = (
        "scenario\treaction\tattacker_id\ttarget_id\tmove_name\tstat\tapplied_delta\tabilities\t"
        "recursive_call_count\trecursive_attacker\trecursive_target\trecursive_move\trecursive_stat\t"
        "recursive_delta\trecursive_effect\tdirect_event_count\n"
    )
    args.output.write_text(
        header + "".join("\t".join(map(str, row)) + "\n" for row in rows),
        encoding="utf-8",
    )
    print(f"wrote {len(rows)} Defiant/Competitive fixtures to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
