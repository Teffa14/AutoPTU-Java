#!/usr/bin/env python3
"""Freeze Flower Veil combat-stage prevention from the pinned Python oracle."""
from __future__ import annotations

import argparse
from pathlib import Path


def between(source: str, start_token: str, end_token: str) -> str:
    start = source.find(start_token)
    end = source.find(end_token, start + len(start_token))
    if start < 0 or end < 0:
        raise RuntimeError(f"unable to isolate pinned oracle window: {start_token}")
    return source[start:end]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = (args.source_root / "auto_ptu" / "rules" / "battle_state.py").read_text(encoding="utf-8")
    helper = between(source, "def _flower_veil_blocker", "def ",)
    branch_start = source.find("flower_veil_blocker = self._flower_veil_blocker(target_id)")
    branch_end = source.find("if not abilities_suppressed", branch_start)
    if branch_start < 0 or branch_end < 0:
        raise RuntimeError("Flower Veil combat-stage branch not found in pinned oracle")
    branch = source[branch_start:branch_end]

    rows = {
        "target_requires_grass": int('== "grass"' in helper.lower()),
        "skips_fainted_or_inactive_holders": int("if mon.fainted or not mon.active" in helper),
        "matches_base_registration": int('mon.has_ability("Flower Veil")' in helper),
        "base_radius": 10 if "else 10" in helper else -1,
        "errata_radius": 5 if 'Flower Veil [Errata]' in helper and "range_limit = 5" in helper else -1,
        "missing_position_accepts_first_holder": int("if mon.position is None or target_pos is None" in helper and "return pid" in helper),
        "no_team_filter": int("team" not in helper.lower() and "controller" not in helper.lower()),
        "external_drop_only": int("delta < 0 and target_id != attacker_id" in source[max(0, branch_start - 120):branch_start + 20]),
        "emits_combat_stage_block": int('"effect": "combat_stage_block"' in branch),
        "returns_before_mutation": int("return" in branch),
        "generic_suppression_not_guarding_flower_veil": int("abilities_suppressed" not in branch),
    }
    expected = {
        "target_requires_grass": 1,
        "skips_fainted_or_inactive_holders": 1,
        "matches_base_registration": 1,
        "base_radius": 10,
        "errata_radius": 5,
        "missing_position_accepts_first_holder": 1,
        "no_team_filter": 1,
        "external_drop_only": 1,
        "emits_combat_stage_block": 1,
        "returns_before_mutation": 1,
        "generic_suppression_not_guarding_flower_veil": 1,
    }
    if rows != expected:
        raise RuntimeError(f"pinned oracle Flower Veil contract changed: {rows}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("contract\tvalue\n" + "".join(f"{k}\t{v}\n" for k, v in rows.items()), encoding="utf-8")


if __name__ == "__main__":
    main()
