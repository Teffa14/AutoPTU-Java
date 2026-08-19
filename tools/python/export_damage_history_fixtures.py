#!/usr/bin/env python3
"""Export the pinned Python BattleState round-damage history contract."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    sys.path.insert(0, str(args.source_root.resolve()))
    from auto_ptu.rules.battle_state import BattleState

    battle = object.__new__(BattleState)
    battle.damage_this_round = set()
    battle.damage_taken_from = {}

    BattleState._record_damage_exchange(battle, "attacker-a", "target")
    BattleState._record_damage_exchange(battle, "attacker-b", "target")
    BattleState._record_damage_exchange(battle, "", "source-less-target")

    field = BattleState.__dataclass_fields__["damage_received_this_round"]
    received = field.default_factory()
    received["target"] = received.get("target", 0) + 7
    received["target"] = received.get("target", 0) + 5

    rows = [
        ("damage_this_round", ",".join(sorted(battle.damage_this_round))),
        (
            "target_sources",
            ",".join(sorted(battle.damage_taken_from.get("target", set()))),
        ),
        (
            "source_less_target_sources",
            ",".join(sorted(battle.damage_taken_from.get("source-less-target", set()))),
        ),
        ("damage_received_container", type(received).__name__),
        ("damage_received_accumulated", str(received.get("target", -1))),
    ]

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{name}\t{value}" for name, value in rows) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} Python damage-history oracle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
