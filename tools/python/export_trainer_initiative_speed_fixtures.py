#!/usr/bin/env python3
"""Export parity fixtures for BattleState._trainer_initiative_speed()."""
from __future__ import annotations

import argparse
import importlib
import sys
from pathlib import Path
from types import SimpleNamespace


class PokemonStub:
    def __init__(self, controller_id: str, speed: int, active: bool, fainted: bool):
        self.controller_id = controller_id
        self.speed = int(speed)
        self.active = bool(active)
        self.fainted = bool(fainted)


def run_case(battle_state_module, calculations, case) -> int:
    trainer_id = "t1"
    battle = SimpleNamespace()
    battle.trainers = {
        trainer_id: SimpleNamespace(speed=case.get("trainer_speed")),
    }
    battle.pokemon = {
        spec[0]: PokemonStub(spec[1], spec[2], spec[3], spec[4])
        for spec in case.get("pokemon", ())
    }

    original_speed_stat = calculations.speed_stat
    calculations.speed_stat = lambda mon: mon.speed
    try:
        return battle_state_module.BattleState._trainer_initiative_speed(battle, trainer_id)
    finally:
        calculations.speed_stat = original_speed_stat


def encode_speeds(case, *, active_only: bool) -> str:
    values = []
    for _, controller_id, speed, active, fainted in case.get("pokemon", ()):
        if controller_id != "t1":
            continue
        if active_only and (not active or fainted):
            continue
        values.append(str(speed))
    return ",".join(values)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    sys.path.insert(0, str(source_root))
    battle_state = importlib.import_module("auto_ptu.rules.battle_state")
    calculations = importlib.import_module("auto_ptu.rules.calculations")

    cases = [
        dict(name="explicit_trainer_speed_wins", trainer_speed=14, pokemon=(("p1", "t1", 30, True, False),)),
        dict(name="explicit_zero_speed_wins", trainer_speed=0, pokemon=(("p1", "t1", 30, True, False),)),
        dict(name="fastest_active_controlled_pokemon", pokemon=(("p1", "t1", 11, True, False), ("p2", "t1", 18, True, False), ("p3", "t1", 25, False, False))),
        dict(name="fainted_active_is_ignored", pokemon=(("p1", "t1", 22, True, True), ("p2", "t1", 13, True, False))),
        dict(name="other_trainer_is_ignored", pokemon=(("p1", "t2", 99, True, False), ("p2", "t1", 12, True, False))),
        dict(name="fallback_to_inactive_roster", pokemon=(("p1", "t1", 9, False, False), ("p2", "t1", 17, False, False))),
        dict(name="fallback_roster_includes_fainted", pokemon=(("p1", "t1", 21, False, True), ("p2", "t1", 8, False, False))),
        dict(name="empty_roster_is_zero", pokemon=()),
    ]

    rows = ["name\ttrainer_speed\tactive_speeds\troster_speeds\texpected"]
    for case in cases:
        expected = run_case(battle_state, calculations, case)
        trainer_speed = "" if case.get("trainer_speed") is None else str(case["trainer_speed"])
        rows.append("\t".join([
            case["name"],
            trainer_speed,
            encode_speeds(case, active_only=True),
            encode_speeds(case, active_only=False),
            str(expected),
        ]))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote {len(cases)} trainer initiative speed fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
