#!/usr/bin/env python3
"""Export action-space geometry from the pinned Python AutoPTU oracle."""
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass, field
from pathlib import Path
from types import SimpleNamespace


@dataclass
class FixtureGrid:
    width: int
    height: int
    blockers: set[tuple[int, int]] = field(default_factory=set)
    tile_types: dict[tuple[int, int], str] = field(default_factory=dict)

    def __post_init__(self):
        self.tiles = {coord: {"type": tile_type} for coord, tile_type in self.tile_types.items()}

    def in_bounds(self, coord):
        return 0 <= coord[0] < self.width and 0 <= coord[1] < self.height


class FixtureActor:
    def __init__(self, position, *, overland=0):
        self.position = position
        self._overland = overland
        self.spec = SimpleNamespace(movement={})

    def movement_speed(self, mode, *, weather=None):
        return self._overland if mode == "overland" else 0

    def has_temporary_effect(self, name):
        return False

    def can_fly(self):
        return False

    def can_swim(self):
        return False

    def can_burrow(self):
        return False

    def can_phase(self):
        return False

    def has_status(self, name):
        return False

    def has_trainer_feature(self, name):
        return False

    def skill_rank(self, name):
        return 0


class FixtureBattle:
    def __init__(self, grid, actor):
        self.grid = grid
        self.pokemon = {"actor": actor}
        self.trainers = {}
        self.weather = None

    def effective_weather_for_actor(self, actor):
        return None

    def _matches_naturewalk_terrain(self, actor):
        return False

    def _position_can_fit(self, actor_id, coord):
        return True

    def _combatant_skill_rank(self, actor, skill_name, actor_id=None):
        return 0


def key_move(move_id: str, mode: str, target_id: str, anchor: tuple[int, int], action="standard") -> str:
    return f"move|actor|{move_id}|{mode}|{target_id}|{anchor[0]},{anchor[1]}|{action}"


def join(values) -> str:
    return ";".join(sorted(values))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    sys.path.insert(0, str(args.source_root.resolve()))
    from auto_ptu.data_models import MoveSpec
    from auto_ptu.rules import movement, targeting

    def move(target_kind: str, target_range: int | None, area_kind=None, area_value=None):
        return MoveSpec(
            name="Fixture Move",
            type="Normal",
            target_kind=target_kind,
            range_kind=target_kind,
            target_range=target_range,
            range_value=target_range,
            area_kind=area_kind,
            area_value=area_value,
            range_text=target_kind,
        )

    scenarios: list[tuple[str, str]] = []

    grid = FixtureGrid(5, 5)
    actor = FixtureActor((2, 2), overland=1)
    reachable = movement.legal_shift_tiles(FixtureBattle(grid, actor), "actor")
    scenarios.append((
        "shift_walking_1",
        join(f"shift|actor|{x},{y}" for x, y in reachable if (x, y) != actor.position),
    ))

    melee = move("Melee", 1)
    direct_targets = [
        ("touching", (2, 0), "Medium"),
        ("too-far", (3, 0), "Medium"),
    ]
    melee_keys = []
    for target_id, anchor, size in direct_targets:
        if targeting.is_target_in_range(
            (0, 0), anchor, melee,
            attacker_size="Large", target_size=size, grid=FixtureGrid(8, 8)
        ):
            melee_keys.append(key_move("tackle", "combatant", target_id, anchor))
    scenarios.append(("large_melee_targets", join(melee_keys)))

    ranged = move("Ranged", 3)
    los_blockers = {(1, 2)}
    ranged_targets = [
        ("near", (3, 1), "Medium"),
        ("blocked", (1, 3), "Medium"),
        ("far", (5, 5), "Medium"),
    ]
    ranged_keys = []
    for target_id, anchor, size in ranged_targets:
        if not targeting.is_target_in_range(
            (1, 1), anchor, ranged,
            attacker_size="Medium", target_size=size, grid=FixtureGrid(7, 7)
        ):
            continue
        if not targeting.line_of_sight_clear(FixtureGrid(7, 7), (1, 1), anchor, los_blockers):
            continue
        ranged_keys.append(key_move("water-gun", "combatant", target_id, anchor))
    scenarios.append(("ranged_los_targets", join(ranged_keys)))

    line = move("Self", 0, "Line", 2)
    line_keys = [
        key_move("flamethrower", "tile", "", anchor)
        for anchor in targeting.target_anchor_tiles(FixtureGrid(5, 5), (2, 2), line)
    ]
    scenarios.append(("line_tile_aim", join(line_keys)))

    self_move = move("Self", 0)
    self_keys = []
    if targeting.normalized_target_kind(self_move) == "self" and not targeting.move_requires_target(self_move):
        self_keys.append(key_move("focus", "self", "actor", (1, 1)))
    scenarios.append(("self_choice", join(self_keys)))

    field_move = move("Field", None)
    field_keys = []
    if targeting.normalized_target_kind(field_move) == "field" and not targeting.move_requires_target(field_move):
        field_keys.append(key_move("weather", "field", "", (1, 1)))
    scenarios.append(("field_choice", join(field_keys)))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(f"{name}\t{value}" for name, value in scenarios) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(scenarios)} Python action-space oracle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
