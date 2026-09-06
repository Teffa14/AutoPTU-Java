#!/usr/bin/env python3
"""Freeze Psionic Overload TURN_END behavior from the pinned Python AutoPTU oracle."""

from __future__ import annotations

import argparse
import csv
import random
import sys
from pathlib import Path


def _pokemon(source_root: Path, *, controller: str, hp_stat: int = 30):
    if str(source_root.resolve()) not in sys.path:
        sys.path.insert(0, str(source_root.resolve()))
    from auto_ptu.data_models import PokemonSpec
    from auto_ptu.rules import PokemonState

    spec = PokemonSpec(
        species="Oracle Dummy",
        level=20,
        types=["Normal"],
        hp_stat=hp_stat,
        atk=10,
        defense=10,
        spatk=10,
        spdef=10,
        spd=10,
        moves=[],
        abilities=[],
        items=[],
        movement={"overland": 4},
        weight=5,
        gender="",
    )
    return PokemonState(
        spec=spec,
        controller_id=controller,
        position=(0, 0),
        active=True,
    )


def _run_case(source_root: Path, case: str, *, hp: int | None = None, temp_hp: int = 0,
              lifted: bool = True, fainted: bool = False, bindings: tuple[str, ...] = ("trainer-a",)):
    if str(source_root.resolve()) not in sys.path:
        sys.path.insert(0, str(source_root.resolve()))
    from auto_ptu.rules import BattleState, GridState, TurnPhase

    actor = _pokemon(source_root, controller="actor")
    target = _pokemon(source_root, controller="target")
    if hp is not None:
        target.hp = int(hp)
    target.temp_hp = int(temp_hp)
    target.fainted = bool(fainted)
    if lifted:
        target.statuses.append({"name": "Lifted"})
    for source_id in bindings:
        target.add_temporary_effect("psionic_overload_telekinesis", source_id=source_id)

    battle = BattleState(
        trainers={},
        pokemon={"actor": actor, "target": target},
        grid=GridState(width=8, height=8),
        rng=random.Random(1),
    )
    battle.round = 2
    battle.phase = TurnPhase.END
    battle.current_actor_id = "actor"
    log_before = len(battle.log)
    hp_before = int(target.hp)
    temp_before = int(target.temp_hp)

    battle.end_turn()

    emitted = battle.log[log_before:]
    relevant = [
        event for event in emitted
        if event.get("type") == "turn_end"
        or (
            event.get("type") == "trainer_feature"
            and event.get("feature") == "Psionic Overload"
            and event.get("effect") == "telekinesis_tick"
        )
    ]
    feature_event = next((event for event in relevant if event.get("effect") == "telekinesis_tick"), None)
    event_order = ",".join(
        "telekinesis_tick" if event.get("effect") == "telekinesis_tick" else str(event.get("type", ""))
        for event in relevant
    )
    remaining = target.get_temporary_effects("psionic_overload_telekinesis")

    return (
        case,
        int(target.max_hp()),
        hp_before,
        temp_before,
        "1" if lifted else "0",
        "1" if fainted else "0",
        ",".join(bindings),
        int(target.hp),
        int(target.temp_hp),
        len(remaining),
        "" if feature_event is None else str(feature_event.get("actor", "")),
        0 if feature_event is None else int(feature_event.get("amount", 0) or 0),
        -1 if feature_event is None else int(feature_event.get("target_hp", -1)),
        event_order,
        "1" if bool(target.fainted) else "0",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    probe = _pokemon(args.source_root, controller="probe")
    max_hp = int(probe.max_hp())
    tick = max(1, max_hp // 10)
    rows = [
        _run_case(args.source_root, "nonlethal", hp=max_hp, bindings=("trainer-a", "trainer-b")),
        _run_case(args.source_root, "lethal", hp=max(1, tick - 1)),
        _run_case(args.source_root, "temporary_hp", hp=max_hp, temp_hp=tick + 2),
        _run_case(args.source_root, "lost_lifted", hp=max_hp, lifted=False, bindings=("trainer-a", "trainer-b")),
        _run_case(args.source_root, "already_fainted", hp=0, fainted=True),
    ]

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow((
            "case", "max_hp", "hp_before", "temp_hp_before", "lifted", "fainted_before", "binding_sources",
            "hp_after", "temp_hp_after", "bindings_after", "event_actor", "event_amount", "event_target_hp",
            "event_order", "fainted_after",
        ))
        writer.writerows(rows)


if __name__ == "__main__":
    main()
