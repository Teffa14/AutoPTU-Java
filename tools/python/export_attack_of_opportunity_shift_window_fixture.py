#!/usr/bin/env python3
"""Freeze Attack of Opportunity Shift-window discovery facts from the pinned Python oracle."""
from __future__ import annotations

import argparse
import importlib.util
import json
import sys
import types
from pathlib import Path

SHIFT_SENTENCE = "An adjacent foe Shifts out of a Square adjacent to you."
ONCE_SENTENCE = "You may use Attack of Opportunity only once per round."
BLOCKED_SENTENCE = "Attacks of Opportunity cannot be made by Sleeping, Flinched, or Paralyzed targets."


def normalize(value: str) -> str:
    return "_".join(part for part in "".join(ch.lower() if ch.isalnum() else " " for ch in value).split())


def load_targeting(source_root: Path):
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
        raise RuntimeError(f"cannot load targeting oracle from {module_path}")
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
    demo = source_root / "auto_ptu/data/campaigns/keyword_demos/interrupt-1_demo.json"
    payload = json.loads(demo.read_text(encoding="utf-8"))
    owner = payload["players"][0]
    non_owner = payload["foes"][0]
    reaction = next(move for move in owner["moves"] if move["name"] == "Attack of Opportunity")
    text = reaction["effects_text"]
    for sentence in (SHIFT_SENTENCE, ONCE_SENTENCE, BLOCKED_SENTENCE):
        if sentence not in text:
            raise AssertionError(f"Attack of Opportunity contract changed: {sentence}")

    owner_moves = [move["name"] for move in owner.get("moves", [])]
    non_owner_moves = [move["name"] for move in non_owner.get("moves", [])]
    if not any(normalize(move) == "attack_of_opportunity" for move in owner_moves):
        raise AssertionError("oracle owner lost Attack of Opportunity")
    if any(normalize(move) == "attack_of_opportunity" for move in non_owner_moves):
        raise AssertionError("negative ownership control now owns Attack of Opportunity")

    targeting = load_targeting(source_root)
    cases = (
        ("owned_adjacent", owner_moves, "", 0, 0, 1, 0, "blue", "red"),
        ("owned_diagonal", owner_moves, "", 0, 0, 1, 1, "blue", "red"),
        ("owned_sleeping", owner_moves, "Sleeping", 0, 0, 1, 0, "blue", "red"),
        ("owned_paralyzed", owner_moves, "Paralyzed", 0, 0, 1, 0, "blue", "red"),
        ("owned_far", owner_moves, "", 0, 0, 3, 0, "blue", "red"),
        ("not_owned_adjacent", non_owner_moves, "", 0, 0, 1, 0, "blue", "red"),
        ("owned_allied", owner_moves, "", 0, 0, 1, 0, "blue", "blue"),
    )

    rows = ["case\tmove_ids\tstatus\treactor_x\treactor_y\tactor_x\tactor_y\treactor_team\tactor_team\texpected"]
    blocked = {"Sleeping", "Flinched", "Paralyzed"}
    for name, moves, status, rx, ry, ax, ay, reactor_team, actor_team in cases:
        distance = targeting.footprint_distance((rx, ry), "Medium", (ax, ay), "Medium")
        owns = any(normalize(move) == "attack_of_opportunity" for move in moves)
        expected = owns and status not in blocked and reactor_team != actor_team and distance == 1
        rows.append("\t".join(map(str, (
            name, ",".join(moves), status, rx, ry, ax, ay, reactor_team, actor_team, str(expected).lower()
        ))))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
