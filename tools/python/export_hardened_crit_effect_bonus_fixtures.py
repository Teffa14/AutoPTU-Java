#!/usr/bin/env python3
"""Export parity fixtures for PokemonState.hardened_crit_effect_bonus()."""
from __future__ import annotations

import argparse
import importlib
import sys
from pathlib import Path


class TrainerStub:
    def __init__(self, intimidate_rank: int):
        self.skills = {"intimidate": int(intimidate_rank)}

    def skill_rank(self, name: str) -> int:
        if not name:
            return 0
        return int(self.skills.get(str(name).lower(), 0) or 0)


class PokemonStub:
    def __init__(
        self,
        pokemon_state_cls,
        *,
        injuries: int,
        current_round: int,
        hardened_expiry,
        press_on_feature: bool,
        press_on_active: bool,
        intimidate_rank: int,
    ):
        self.injuries = int(injuries)
        self.controller_id = "trainer"
        self._press_on_feature = bool(press_on_feature)
        self._effects = []
        hardened = {"name": "hardened"}
        if hardened_expiry is not None:
            hardened["expires_round"] = hardened_expiry
        self._effects.append(hardened)
        if press_on_active:
            self._effects.append({"name": "press_on_active"})
        self.battle = type("BattleStub", (), {})()
        self.battle.round = int(current_round)
        self.battle.trainers = {"trainer": TrainerStub(intimidate_rank)}
        self._pokemon_state_cls = pokemon_state_cls

    def get_temporary_effects(self, name: str):
        key = str(name or "").strip().lower()
        return [entry for entry in self._effects if str(entry.get("name") or "").strip().lower() == key]

    def has_trainer_feature(self, name: str) -> bool:
        return self._press_on_feature and str(name or "").strip().lower() == "press on!"

    def is_hardened(self) -> bool:
        return self._pokemon_state_cls.is_hardened(self)

    def is_pressing_on(self) -> bool:
        return self._pokemon_state_cls.is_pressing_on(self)

    def hardened_bonus_multiplier(self, battle=None) -> int:
        return self._pokemon_state_cls.hardened_bonus_multiplier(self, battle)

    def hardened_crit_effect_bonus(self, battle=None) -> int:
        return self._pokemon_state_cls.hardened_crit_effect_bonus(self, battle)


def encode_expiry(value) -> str:
    if value is None:
        return ""
    return str(value).replace("\t", " ").replace("\n", " ")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    sys.path.insert(0, str(source_root))
    battle_state = importlib.import_module("auto_ptu.rules.battle_state")

    cases = [
        dict(name="below_injury_threshold", injuries=2, current_round=4, expiry=None, feature=False, active=False, rank=0),
        dict(name="hardened_three_injuries", injuries=3, current_round=4, expiry=None, feature=False, active=False, rank=0),
        dict(name="hardened_five_injuries", injuries=5, current_round=4, expiry=None, feature=False, active=False, rank=0),
        dict(name="hardened_expired", injuries=4, current_round=4, expiry=3, feature=False, active=False, rank=0),
        dict(name="hardened_expires_this_round", injuries=4, current_round=4, expiry=4, feature=False, active=False, rank=0),
        dict(name="hardened_invalid_expiry_stays_active", injuries=4, current_round=4, expiry="not-a-round", feature=False, active=False, rank=0),
        dict(name="press_on_active_without_feature", injuries=3, current_round=4, expiry=None, feature=False, active=True, rank=8),
        dict(name="press_on_feature_without_active", injuries=3, current_round=4, expiry=None, feature=True, active=False, rank=8),
        dict(name="press_on_rank_five", injuries=3, current_round=4, expiry=None, feature=True, active=True, rank=5),
        dict(name="press_on_rank_six_doubles", injuries=3, current_round=4, expiry=None, feature=True, active=True, rank=6),
        dict(name="press_on_many_injuries_doubles", injuries=7, current_round=4, expiry=None, feature=True, active=True, rank=8),
        dict(name="press_on_cannot_bypass_injury_threshold", injuries=2, current_round=4, expiry=None, feature=True, active=True, rank=8),
    ]

    rows = ["name\tcurrent_round\tinjuries\thardened_expiry\tpress_on_feature\tpress_on_active\tintimidate_rank\texpected_bonus"]
    for case in cases:
        pokemon = PokemonStub(
            battle_state.PokemonState,
            injuries=case["injuries"],
            current_round=case["current_round"],
            hardened_expiry=case["expiry"],
            press_on_feature=case["feature"],
            press_on_active=case["active"],
            intimidate_rank=case["rank"],
        )
        expected = pokemon.hardened_crit_effect_bonus(pokemon.battle)
        rows.append("\t".join([
            case["name"],
            str(case["current_round"]),
            str(case["injuries"]),
            encode_expiry(case["expiry"]),
            "1" if case["feature"] else "0",
            "1" if case["active"] else "0",
            str(case["rank"]),
            str(expected),
        ]))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"wrote {len(cases)} Hardened crit/effect fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
