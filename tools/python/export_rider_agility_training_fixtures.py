#!/usr/bin/env python3
"""Export parity fixtures for BattleState._rider_agility_training_doubled()."""
from __future__ import annotations

import argparse
import importlib
import sys
from pathlib import Path


class PokemonStub:
    def __init__(self, *, rider_feature: bool = False, agility_training: bool = False):
        self._rider_feature = bool(rider_feature)
        self._agility_training = bool(agility_training)

    def has_trainer_feature(self, name: str) -> bool:
        return self._rider_feature and str(name or "").strip().lower() == "rider"

    def get_temporary_effects(self, name: str):
        if self._agility_training and str(name or "").strip().lower() == "agility_training":
            return [{"name": "agility_training"}]
        return []


class BattleStub:
    pass


def encode_pairs(pairs: dict[str, str]) -> str:
    return ",".join(f"{rider}:{mount}" for rider, mount in pairs.items())


def encode_ids(values) -> str:
    return ",".join(values)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    sys.path.insert(0, str(source_root))
    battle_state = importlib.import_module("auto_ptu.rules.battle_state")

    BattleStub._mounted_mount_id = battle_state.BattleState._mounted_mount_id
    BattleStub._mounted_rider_id = battle_state.BattleState._mounted_rider_id
    BattleStub._rider_agility_training_doubled = battle_state.BattleState._rider_agility_training_doubled

    cases = [
        dict(name="unmounted_actor", actor="mount", pairs={}, existing=["mount"], rider=[], agility=["mount"]),
        dict(name="mount_actor_doubles", actor="mount", pairs={"rider": "mount"}, existing=["rider", "mount"], rider=["rider"], agility=["mount"]),
        dict(name="rider_actor_doubles", actor="rider", pairs={"rider": "mount"}, existing=["rider", "mount"], rider=["rider"], agility=["mount"]),
        dict(name="missing_rider_feature", actor="mount", pairs={"rider": "mount"}, existing=["rider", "mount"], rider=[], agility=["mount"]),
        dict(name="missing_agility_training", actor="mount", pairs={"rider": "mount"}, existing=["rider", "mount"], rider=["rider"], agility=[]),
        dict(name="missing_rider_actor", actor="mount", pairs={"rider": "mount"}, existing=["mount"], rider=["rider"], agility=["mount"]),
        dict(name="missing_mount_actor", actor="rider", pairs={"rider": "mount"}, existing=["rider"], rider=["rider"], agility=["mount"]),
        dict(name="unrelated_actor", actor="other", pairs={"rider": "mount"}, existing=["rider", "mount", "other"], rider=["rider"], agility=["mount"]),
        dict(name="second_pair_mount", actor="mount2", pairs={"rider1": "mount1", "rider2": "mount2"}, existing=["rider1", "mount1", "rider2", "mount2"], rider=["rider2"], agility=["mount2"]),
    ]

    rows = ["name\tactor_id\tmounted_pairs\texisting_actor_ids\trider_feature_actor_ids\tagility_training_actor_ids\texpected_doubled"]
    for case in cases:
        battle = BattleStub()
        battle.mounted_pairs = dict(case["pairs"])
        battle.pokemon = {}
        for actor_id in case["existing"]:
            battle.pokemon[actor_id] = PokemonStub(
                rider_feature=actor_id in case["rider"],
                agility_training=actor_id in case["agility"],
            )
        expected = bool(battle._rider_agility_training_doubled(case["actor"]))
        rows.append("\t".join([
            case["name"],
            case["actor"],
            encode_pairs(case["pairs"]),
            encode_ids(case["existing"]),
            encode_ids(case["rider"]),
            encode_ids(case["agility"]),
            "1" if expected else "0",
        ]))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote {len(cases)} Rider Agility Training fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
