#!/usr/bin/env python3
"""Freeze Attack of Opportunity Shift-trigger geometry from the pinned Python oracle."""
from __future__ import annotations

import argparse
import importlib.util
import json
import sys
import types
from pathlib import Path

SHIFT_SENTENCE = "An adjacent foe Shifts out of a Square adjacent to you."

# name, reactor x/y/size/team, actor origin x/y/size/team, destination x/y
CASES = (
    ("adjacent_cardinal", 0, 0, "Medium", "blue", 1, 0, "Medium", "red", 2, 0),
    ("adjacent_diagonal", 0, 0, "Medium", "blue", 1, 1, "Medium", "red", 2, 2),
    ("adjacent_to_adjacent", 0, 0, "Medium", "blue", 1, 0, "Medium", "red", 0, 1),
    ("non_adjacent_origin", 0, 0, "Medium", "blue", 2, 0, "Medium", "red", 3, 0),
    ("allied_shift", 0, 0, "Medium", "blue", 1, 0, "Medium", "blue", 2, 0),
    ("large_footprint_adjacent", 0, 0, "Large", "blue", 2, 0, "Medium", "red", 3, 0),
)


def load_targeting(source_root: Path):
    """Load only oracle targeting.py without importing the rest of the application."""
    package_root = source_root / "auto_ptu"
    rules_root = package_root / "rules"

    auto_ptu = types.ModuleType("auto_ptu")
    auto_ptu.__path__ = [str(package_root)]
    rules = types.ModuleType("auto_ptu.rules")
    rules.__path__ = [str(rules_root)]
    data_models = types.ModuleType("auto_ptu.data_models")
    data_models.MoveSpec = object
    sys.modules["auto_ptu"] = auto_ptu
    sys.modules["auto_ptu.rules"] = rules
    sys.modules["auto_ptu.data_models"] = data_models

    module_path = rules_root / "targeting.py"
    spec = importlib.util.spec_from_file_location("auto_ptu.rules.targeting", module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load Python targeting oracle from {module_path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    demo = source_root / "auto_ptu" / "data" / "campaigns" / "keyword_demos" / "interrupt-1_demo.json"
    payload = json.loads(demo.read_text(encoding="utf-8"))
    move = next(move for move in payload["players"][0]["moves"] if move["name"] == "Attack of Opportunity")
    if SHIFT_SENTENCE not in move["effects_text"]:
        raise AssertionError(f"Attack of Opportunity Shift trigger text changed: {SHIFT_SENTENCE}")

    targeting = load_targeting(source_root)
    rows = [
        "case\treactor_x\treactor_y\treactor_size\treactor_team\t"
        "actor_origin_x\tactor_origin_y\tactor_size\tactor_team\tdestination_x\tdestination_y\texpected"
    ]
    for case in CASES:
        (
            name,
            reactor_x,
            reactor_y,
            reactor_size,
            reactor_team,
            actor_x,
            actor_y,
            actor_size,
            actor_team,
            destination_x,
            destination_y,
        ) = case
        distance = targeting.footprint_distance(
            (reactor_x, reactor_y), reactor_size, (actor_x, actor_y), actor_size
        )
        expected = reactor_team != actor_team and distance == 1
        rows.append(
            "\t".join(
                map(
                    str,
                    (
                        name,
                        reactor_x,
                        reactor_y,
                        reactor_size,
                        reactor_team,
                        actor_x,
                        actor_y,
                        actor_size,
                        actor_team,
                        destination_x,
                        destination_y,
                        str(expected).lower(),
                    ),
                )
            )
        )

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote Attack of Opportunity Shift trigger fixture to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
