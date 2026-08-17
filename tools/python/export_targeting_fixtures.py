#!/usr/bin/env python3
"""Export deterministic targeting results from the pinned Python AutoPTU oracle."""
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path


@dataclass
class FixtureGrid:
    width: int
    height: int

    def in_bounds(self, coord: tuple[int, int]) -> bool:
        return 0 <= coord[0] < self.width and 0 <= coord[1] < self.height


def coords(value) -> str:
    return ";".join(f"{x},{y}" for x, y in sorted(value))


def boolean(value: bool) -> str:
    return "true" if value else "false"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.data_models import MoveSpec
    from auto_ptu.rules import targeting

    def move(target_kind: str, target_range: int | None, area_kind=None, area_value=None, *, range_text=""):
        return MoveSpec(
            name="Fixture Move",
            type="Normal",
            target_kind=target_kind,
            range_kind=target_kind,
            target_range=target_range,
            range_value=target_range,
            area_kind=area_kind,
            area_value=area_value,
            range_text=range_text or target_kind,
        )

    grid10 = FixtureGrid(10, 10)
    scenarios: list[tuple[str, str]] = []

    spec = MoveSpec(
        name="Normalize",
        type="Normal",
        target_kind="Ranged, 1 Target",
        range_kind="Ranged",
        target_range=6,
        area_kind="Close Blast 3",
        area_value=3,
    )
    scenarios.append(("normalize_target", targeting.normalized_target_kind(spec)))
    scenarios.append(("normalize_area", targeting.normalized_area_kind(spec)))
    scenarios.append(("chebyshev_1_1_to_5_3", str(targeting.chebyshev_distance((1, 1), (5, 3)))))
    scenarios.append(("large_footprint", coords(targeting.footprint_tiles((5, 5), "Large"))))
    scenarios.append(("huge_footprint_count", str(len(targeting.footprint_tiles((5, 5), "Huge")))))
    scenarios.append(("gigantic_footprint_count", str(len(targeting.footprint_tiles((5, 5), "Gigantic")))))
    scenarios.append(("large_to_medium_distance", str(targeting.footprint_distance((0, 0), "Large", (2, 0), "Medium"))))

    melee = move("Melee", 1)
    scenarios.append((
        "large_melee_at_2",
        boolean(targeting.is_target_in_range((0, 0), (2, 0), melee, attacker_size="Large", target_size="Medium", grid=grid10)),
    ))
    scenarios.append((
        "large_melee_at_3",
        boolean(targeting.is_target_in_range((0, 0), (3, 0), melee, attacker_size="Large", target_size="Medium", grid=grid10)),
    ))

    scenarios.append((
        "line_east_3",
        coords(targeting.affected_tiles(grid10, (2, 2), (4, 2), move("Self", 0, "Line", 3))),
    ))
    scenarios.append((
        "cone_east_2",
        coords(targeting.affected_tiles(grid10, (2, 2), (4, 2), move("Self", 0, "Cone", 2))),
    ))
    scenarios.append((
        "closeblast_east_2",
        coords(targeting.affected_tiles(grid10, (2, 2), (3, 2), move("Self", 0, "CloseBlast", 2))),
    ))
    scenarios.append((
        "blast_3_center_5_5",
        coords(targeting.affected_tiles(grid10, (1, 1), (5, 5), move("Ranged", 6, "Blast", 3))),
    ))
    scenarios.append((
        "burst_1_corner",
        coords(targeting.affected_tiles(FixtureGrid(2, 2), (0, 0), (0, 0), move("Ranged", 6, "Burst", 1))),
    ))
    scenarios.append((
        "los_blocked",
        boolean(targeting.line_of_sight_clear(grid10, (0, 0), (4, 2), {(2, 1)})),
    ))
    scenarios.append((
        "los_clear_other_cell",
        boolean(targeting.line_of_sight_clear(grid10, (0, 0), (4, 2), {(2, 2)})),
    ))
    scenarios.append((
        "ranged_anchor_count",
        str(len(targeting.target_anchor_tiles(FixtureGrid(5, 5), (2, 2), move("Ranged", 2)))),
    ))
    scenarios.append((
        "melee_anchor_count",
        str(len(targeting.target_anchor_tiles(FixtureGrid(5, 5), (2, 2), move("Melee", 1)))),
    ))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(f"{name}\t{value}" for name, value in scenarios) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(scenarios)} Python targeting oracle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
