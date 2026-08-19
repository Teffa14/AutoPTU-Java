#!/usr/bin/env python3
"""Freeze delayed-hit scheduling and due/future partition behavior from Python AutoPTU."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any, Optional, Tuple


class CaptureBattle:
    def __init__(self, round_number: int) -> None:
        self.round = round_number
        self.delayed_hits: list[dict[str, Any]] = []
        self.resolved: list[dict[str, Any]] = []

    def resolve_move_targets(
        self,
        *,
        attacker_id: str,
        move: Any,
        target_id: Optional[str],
        target_position: Optional[Tuple[int, int]],
    ) -> None:
        self.resolved.append(
            {
                "attacker_id": attacker_id,
                "move": move.name,
                "target_id": target_id,
                "target_position": target_position,
            }
        )


def text(value: object) -> str:
    return "" if value is None else str(value)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    sys.path.insert(0, str(source_root))
    from auto_ptu.data_models import MoveSpec  # noqa: PLC0415
    from auto_ptu.rules.hooks.move_effect_tools import (  # noqa: PLC0415
        resolve_delayed_hits,
        schedule_delayed_hit,
    )

    battle = CaptureBattle(round_number=3)
    fixtures = [
        ("future", "alpha", MoveSpec("Future Sight", "Psychic"), "target-a", None, 4, "future_sight"),
        ("due_target", "beta", MoveSpec("Doom Desire", "Steel"), "target-b", None, 3, "doom_desire"),
        ("due_position", "gamma", MoveSpec("Delayed Burst", "Fire"), None, (7, 9), 2, "delayed_burst"),
    ]
    for _, attacker_id, move, target_id, target_position, trigger_round, effect in fixtures:
        schedule_delayed_hit(
            battle,
            attacker_id=attacker_id,
            move=move,
            target_id=target_id,
            target_position=target_position,
            trigger_round=trigger_round,
            effect=effect,
        )

    resolve_delayed_hits(battle)

    due_index = {entry["move"]: index for index, entry in enumerate(battle.resolved)}
    remaining_index = {
        entry["move"].name: index for index, entry in enumerate(battle.delayed_hits)
    }

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    rows: list[str] = []
    for scenario, attacker_id, move, target_id, target_position, trigger_round, effect in fixtures:
        if move.name in due_index:
            outcome = "due"
            index = due_index[move.name]
        else:
            outcome = "remaining"
            index = remaining_index[move.name]
        x = target_position[0] if target_position is not None else None
        y = target_position[1] if target_position is not None else None
        rows.append(
            "\t".join(
                [
                    scenario,
                    attacker_id,
                    move.name,
                    text(target_id),
                    text(x),
                    text(y),
                    str(trigger_round),
                    effect,
                    outcome,
                    str(index),
                ]
            )
        )
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} Python delayed-hit fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
