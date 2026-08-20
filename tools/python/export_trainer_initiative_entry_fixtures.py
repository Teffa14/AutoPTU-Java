#!/usr/bin/env python3
"""Export parity fixtures for trainer InitiativeEntry construction in _build_initiative_order()."""
from __future__ import annotations

import argparse
import importlib
import sys
from pathlib import Path
from types import SimpleNamespace


def run_case(battle_state_module, case):
    trainer_id = case.get("trainer_id", "t1")
    trainer = SimpleNamespace(
        team=case.get("team", "alpha"),
        identifier=case.get("identifier", trainer_id),
        initiative_bonus=lambda: int(case.get("initiative_bonus", 0)),
    )
    battle = SimpleNamespace(
        trainers={trainer_id: trainer},
        pokemon={},
        tailwind_teams=set(case.get("tailwind_teams", ())),
        _trainer_initiative_speed=lambda requested_id: int(case.get("speed", 0)),
        _room_effect_active=lambda _name: False,
        is_league_battle=lambda: False,
    )
    entries = battle_state_module.BattleState._build_initiative_order(battle)
    if len(entries) != 1:
        raise AssertionError(f"expected one trainer entry, got {len(entries)}")
    return entries[0]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    sys.path.insert(0, str(source_root))
    battle_state = importlib.import_module("auto_ptu.rules.battle_state")

    cases = [
        dict(name="base", trainer_id="trainer-a", speed=12, initiative_bonus=3, team="alpha"),
        dict(name="tailwind_by_team", trainer_id="trainer-a", speed=12, initiative_bonus=3, team="alpha", tailwind_teams=("alpha",)),
        dict(name="tailwind_identifier_fallback", trainer_id="trainer-a", speed=8, initiative_bonus=1, team="", identifier="trainer-a", tailwind_teams=("trainer-a",)),
        dict(name="negative_bonus", trainer_id="trainer-a", speed=10, initiative_bonus=-2, team="alpha"),
        dict(name="zero_speed_and_bonus", trainer_id="trainer-a", speed=0, initiative_bonus=0, team="alpha"),
    ]

    rows = ["name\ttrainer_id\tspeed\tinitiative_bonus\ttailwind\tactor_id\tentry_trainer_id\tentry_speed\ttrainer_modifier\troll\ttotal"]
    for case in cases:
        entry = run_case(battle_state, case)
        trainer = SimpleNamespace(team=case.get("team", "alpha"), identifier=case.get("identifier", case.get("trainer_id", "t1")))
        tailwind_key = trainer.team or trainer.identifier
        tailwind = tailwind_key in set(case.get("tailwind_teams", ()))
        rows.append("\t".join([
            case["name"],
            case["trainer_id"],
            str(case["speed"]),
            str(case["initiative_bonus"]),
            "1" if tailwind else "0",
            entry.actor_id,
            entry.trainer_id,
            str(entry.speed),
            str(entry.trainer_modifier),
            str(entry.roll),
            str(entry.total),
        ]))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote {len(cases)} trainer initiative entry fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
