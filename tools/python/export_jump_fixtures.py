#!/usr/bin/env python3
"""Export jump-legality results from the pinned Python AutoPTU oracle."""
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass, field
from pathlib import Path
from types import SimpleNamespace


def coords(value) -> str:
    return ";".join(f"{x},{y}" for x, y in sorted(value))


@dataclass
class FixtureGrid:
    width: int
    height: int
    blockers: set[tuple[int, int]] = field(default_factory=set)
    tile_types: dict[tuple[int, int], str] = field(default_factory=dict)

    def __post_init__(self):
        self.tiles = {
            coord: {"type": tile_type}
            for coord, tile_type in self.tile_types.items()
        }

    def in_bounds(self, coord):
        return 0 <= coord[0] < self.width and 0 <= coord[1] < self.height


class FixtureActor:
    def __init__(
        self,
        position,
        *,
        long_jump=0,
        high_jump=0,
        fly=False,
        swim=False,
        burrow=False,
        phase=False,
        liquefied=False,
        wallrunner=0,
    ):
        self.position = position
        self.spec = SimpleNamespace(movement={"l_jump": long_jump, "h_jump": high_jump})
        self._fly = fly
        self._swim = swim
        self._burrow = burrow
        self._phase = phase
        self._liquefied = liquefied
        self._wallrunner = wallrunner

    def can_fly(self):
        return self._fly

    def can_swim(self):
        return self._swim

    def can_burrow(self):
        return self._burrow

    def can_phase(self):
        return self._phase

    def has_status(self, name):
        return name == "Liquefied" and self._liquefied

    def has_trainer_feature(self, name):
        return name == "Wallrunner" and self._wallrunner > 0

    def skill_rank(self, name):
        return self._wallrunner if name == "acrobatics" else 0


class FixtureBattle:
    def __init__(self, grid, actor, forbidden_fit=None):
        self.grid = grid
        self.pokemon = {"actor": actor}
        self.trainers = {}
        self._forbidden_fit = set(forbidden_fit or set())

    def _position_can_fit(self, actor_id, coord):
        return coord not in self._forbidden_fit

    def _combatant_skill_rank(self, actor, skill_name, actor_id=None):
        return actor.skill_rank(skill_name)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    sys.path.insert(0, str(args.source_root.resolve()))
    from auto_ptu.rules import movement

    scenarios: list[tuple[str, str]] = []

    def run_long(name, grid, actor, forbidden_fit=None):
        battle = FixtureBattle(grid, actor, forbidden_fit=forbidden_fit)
        scenarios.append((name, coords(movement.legal_long_jump_tiles(battle, "actor"))))

    def run_high(name, grid, actor, forbidden_fit=None):
        battle = FixtureBattle(grid, actor, forbidden_fit=forbidden_fit)
        scenarios.append((name, coords(movement.legal_high_jump_tiles(battle, "actor"))))

    run_long(
        "long_basic_2",
        FixtureGrid(7, 7),
        FixtureActor((3, 3), long_jump=2),
    )
    run_long(
        "long_wallrunner_blocked_path",
        FixtureGrid(5, 1, blockers={(1, 0)}),
        FixtureActor((0, 0), long_jump=1, wallrunner=1),
    )
    run_long(
        "long_no_wallrunner_blocked_path",
        FixtureGrid(4, 1, blockers={(1, 0)}),
        FixtureActor((0, 0), long_jump=2),
    )
    run_long(
        "long_water_no_swim",
        FixtureGrid(4, 1, tile_types={(2, 0): "Water"}),
        FixtureActor((0, 0), long_jump=3),
    )
    run_long(
        "long_water_swim",
        FixtureGrid(4, 1, tile_types={(2, 0): "Water"}),
        FixtureActor((0, 0), long_jump=3, swim=True),
    )
    run_long(
        "long_burrow_intermediate_block",
        FixtureGrid(4, 1, blockers={(1, 0)}),
        FixtureActor((0, 0), long_jump=2, burrow=True),
    )
    run_long(
        "long_fit_reject",
        FixtureGrid(3, 1),
        FixtureActor((0, 0), long_jump=2),
        forbidden_fit={(1, 0)},
    )
    run_high(
        "high_basic_2",
        FixtureGrid(7, 7),
        FixtureActor((3, 3), high_jump=2),
    )
    run_high(
        "high_water_no_swim",
        FixtureGrid(3, 1, tile_types={(1, 0): "Water"}),
        FixtureActor((0, 0), high_jump=1),
    )
    run_high(
        "high_blocked_destination",
        FixtureGrid(3, 1, blockers={(1, 0)}),
        FixtureActor((0, 0), high_jump=1),
    )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "".join(f"{name}\t{value}\n" for name, value in scenarios),
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
