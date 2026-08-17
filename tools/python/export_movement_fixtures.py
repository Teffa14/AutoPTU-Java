#!/usr/bin/env python3
"""Export movement results from the pinned Python AutoPTU oracle using tiny mocks."""
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

    def in_bounds(self, coord: tuple[int, int]) -> bool:
        return 0 <= coord[0] < self.width and 0 <= coord[1] < self.height


class FixtureActor:
    def __init__(
        self,
        position,
        *,
        overland=0,
        swim=0,
        sky=0,
        fly=False,
        can_swim=False,
        burrow=False,
        phase=False,
        liquefied=False,
        sprint_multiplier=1.0,
        wallrunner_limit=0,
    ):
        self.position = position
        self._speeds = {"overland": overland, "swim": swim, "sky": sky}
        self._fly = fly
        self._swim = can_swim
        self._burrow = burrow
        self._phase = phase
        self._liquefied = liquefied
        self._sprint_multiplier = sprint_multiplier
        self._wallrunner_limit = wallrunner_limit
        self.spec = SimpleNamespace(movement={})

    def movement_speed(self, mode, *, weather=None):
        return self._speeds.get(mode, 0)

    def has_temporary_effect(self, name):
        if name == "sprint":
            return self._sprint_multiplier != 1.0
        if name == "coaching_sprint":
            return self._sprint_multiplier == 2.0
        return False

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
        return name == "Wallrunner" and self._wallrunner_limit > 0

    def skill_rank(self, name):
        return self._wallrunner_limit if name == "acrobatics" else 0


class FixtureBattle:
    def __init__(self, grid, actor, *, naturewalk=False, forbidden_fit=None):
        self.grid = grid
        self.pokemon = {"actor": actor}
        self.weather = None
        self._naturewalk = naturewalk
        self._forbidden_fit = set(forbidden_fit or set())
        self.trainers = {}

    def effective_weather_for_actor(self, actor):
        return self.weather

    def _matches_naturewalk_terrain(self, actor):
        return self._naturewalk

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

    def run(name, grid, actor, *, naturewalk=False, forbidden_fit=None, penalty=0):
        battle = FixtureBattle(grid, actor, naturewalk=naturewalk, forbidden_fit=forbidden_fit)
        value = movement.legal_shift_tiles(battle, "actor", limit_penalty=penalty)
        scenarios.append((name, coords(value)))

    run(
        "walking_2",
        FixtureGrid(7, 7),
        FixtureActor((3, 3), overland=2),
    )
    run(
        "rough_costs_two",
        FixtureGrid(4, 1, tile_types={(1, 0): "Rough Grass"}),
        FixtureActor((0, 0), overland=2),
    )
    run(
        "naturewalk_rough",
        FixtureGrid(4, 1, tile_types={(1, 0): "Rough Grass"}),
        FixtureActor((0, 0), overland=2),
        naturewalk=True,
    )
    run(
        "swim_limit_two",
        FixtureGrid(4, 1, tile_types={(1, 0): "Water", (2, 0): "Water", (3, 0): "Water"}),
        FixtureActor((0, 0), overland=3, swim=2, can_swim=True),
    )
    run(
        "mixed_water_then_land",
        FixtureGrid(4, 1, tile_types={(1, 0): "Water", (2, 0): "Water"}),
        FixtureActor((0, 0), overland=3, swim=2, can_swim=True),
    )
    run(
        "fly_over_blocker_and_rough",
        FixtureGrid(5, 1, blockers={(1, 0)}, tile_types={(2, 0): "Difficult Rough"}),
        FixtureActor((0, 0), sky=3, fly=True),
    )
    run(
        "wallrunner_crosses_one",
        FixtureGrid(5, 1, blockers={(1, 0)}),
        FixtureActor((0, 0), overland=3, wallrunner_limit=1),
    )
    run(
        "sprint_after_penalty",
        FixtureGrid(8, 1),
        FixtureActor((0, 0), overland=5, sprint_multiplier=1.5),
        penalty=2,
    )
    run(
        "fit_blocks_expansion",
        FixtureGrid(5, 1),
        FixtureActor((0, 0), overland=4),
        forbidden_fit={(2, 0)},
    )

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(f"{name}\t{value}" for name, value in scenarios) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(scenarios)} Python movement oracle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
