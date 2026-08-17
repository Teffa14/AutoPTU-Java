#!/usr/bin/env python3
"""Export shift-application transitions from the pinned Python AutoPTU oracle."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from types import SimpleNamespace


def fixture_state(battle_state, *, blockers=(), speed=6):
    grid = SimpleNamespace(width=6, height=6, blockers=set(blockers))
    mon = SimpleNamespace(name="FixtureMon", spd=speed)
    combatant = SimpleNamespace(mon=mon, x=1, y=1, hp=10)
    token = {"id": "actor", "trainer_id": "trainer", "combatant": combatant}
    state = object.__new__(battle_state.InteractiveBattleState)
    state.status = "player-turn"
    state._current_trainer_id = "trainer"
    state._trainer_map = {"trainer": {"id": "trainer", "controller": "player"}}
    state._token_lookup = {"actor": token}
    state._movement_limits = {"actor": 3}
    state._movement_used = {"actor": False}
    state.plan = SimpleNamespace(grid=grid)
    state._active_token_for_trainer = lambda trainer_id: token if trainer_id == "trainer" else None
    return state, combatant


def rejected(call) -> str:
    try:
        call()
    except ValueError:
        return "true"
    return "false"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    sys.path.insert(0, str(args.source_root.resolve()))
    from auto_ptu import battle_state

    rows: list[tuple[str, str]] = []

    state, combatant = fixture_state(battle_state)
    state.move_token("actor", (2, 1))
    rows.append(("successful_shift", f"{combatant.x},{combatant.y}|used={str(state._movement_used['actor']).lower()}"))
    rows.append(("second_shift_rejected", rejected(lambda: state.move_token("actor", (3, 1)))))

    state, _ = fixture_state(battle_state, blockers={(2, 1)})
    rows.append(("blocked_rejected", rejected(lambda: state.move_token("actor", (2, 1)))))

    state, _ = fixture_state(battle_state)
    rows.append(("too_far_rejected", rejected(lambda: state.move_token("actor", (5, 5)))))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{name}\t{value}" for name, value in rows) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} Python shift-application oracle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
