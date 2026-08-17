#!/usr/bin/env python3
"""Export semantic move-event fields from pinned Python AutoPTU battle_state.format_move_event."""
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path


@dataclass
class FakeMon:
    name: str
    maximum_hp: int

    def max_hp(self) -> int:
        return self.maximum_hp


@dataclass
class FakeCombatant:
    mon: FakeMon
    hp: int


@dataclass
class FakeMove:
    name: str


def stable_key(event: dict) -> str:
    return "|".join(
        [
            "move_resolved",
            str(event["by"]),
            str(event["attacker"]),
            str(event["target"]),
            str(event["move"]),
            str(bool(event["hit"])).lower(),
            str(bool(event["crit"])).lower(),
            str(int(event["damage"])),
            str(int(event["target_hp"])),
        ]
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    sys.path.insert(0, str(args.source_root.resolve()))
    from auto_ptu import battle_state

    scenarios = [
        ("miss", "Player", "pikachu", "bulbasaur", "thunder-shock", False, False, 0, 35),
        ("hit", "Player", "pikachu", "bulbasaur", "thunder-shock", True, False, 12, 23),
        ("critical", "Foe", "charizard", "venusaur", "flamethrower", True, True, 20, 15),
    ]

    rows: list[tuple[str, str]] = []
    original_resolve_hit = battle_state.ptu_engine.resolve_hit
    try:
        for name, label, attacker_name, target_name, move_name, hit, crit, damage, target_hp in scenarios:
            attacker = FakeCombatant(FakeMon(attacker_name, 50), 50)
            defender = FakeCombatant(FakeMon(target_name, 50), 50)
            move = FakeMove(move_name)

            def fake_resolve_hit(_attacker, _defender, _move, _terrain, _rng, *, _hit=hit, _crit=crit, _damage=damage, _target_hp=target_hp):
                _defender.hp = _target_hp
                return {"hit": _hit, "crit": _crit, "damage": _damage}

            battle_state.ptu_engine.resolve_hit = fake_resolve_hit
            event = battle_state.format_move_event(label, attacker, defender, move, object(), object())
            rows.append((name, stable_key(event)))
    finally:
        battle_state.ptu_engine.resolve_hit = original_resolve_hit

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{name}\t{value}" for name, value in rows) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} Python move-event oracle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
