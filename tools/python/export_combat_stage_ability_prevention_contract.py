#!/usr/bin/env python3
"""Freeze target-owned Combat Stage prevention rules from the pinned Python oracle."""
from __future__ import annotations

import argparse
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = (args.source_root / "auto_ptu" / "rules" / "battle_state.py").read_text(encoding="utf-8")
    start = source.find('if not abilities_suppressed and delta < 0 and stat == "def" and target.has_ability("Big Pecks")')
    end = source.find('and target.has_ability("Mirror Armor")', start)
    if start < 0 or end < 0:
        raise RuntimeError("target-owned combat-stage prevention window not found in pinned oracle")
    window = source[start:end]

    conditions = {
        "big_pecks_def_only": 'stat == "def" and target.has_ability("Big Pecks")',
        "hyper_cutter_atk_only": 'stat == "atk" and target.has_ability("Hyper Cutter")',
        "clear_body_external_only": 'target_id != attacker_id and target.has_ability("Clear Body")',
        "full_metal_body_external_only": 'target_id != attacker_id and target.has_ability("Full Metal Body")',
        "white_smoke_external_only": 'target_id != attacker_id and target.has_ability("White Smoke")',
    }
    rows: dict[str, int] = {key: int(token in window) for key, token in conditions.items()}
    rows["all_negative_only"] = int(window.count("delta < 0") >= 5)
    rows["all_suppression_guarded"] = int(window.count("not abilities_suppressed") >= 5)
    rows["all_emit_combat_stage_block"] = int(window.count('"effect": "combat_stage_block"') >= 5)
    rows["all_return_before_mutation"] = int(window.count("                return") >= 5)

    names = ["Big Pecks", "Hyper Cutter", "Clear Body", "Full Metal Body", "White Smoke"]
    positions = [window.find(f'"ability": "{name}"') for name in names]
    rows["python_priority_order"] = int(all(pos >= 0 for pos in positions) and positions == sorted(positions))
    rows["big_pecks_has_no_external_guard"] = int('target_id != attacker_id and target.has_ability("Big Pecks")' not in window)
    rows["hyper_cutter_has_no_external_guard"] = int('target_id != attacker_id and target.has_ability("Hyper Cutter")' not in window)
    rows["event_actor_is_target"] = int(window.count('"actor": target_id') >= 5)
    rows["event_target_is_attacker"] = int(window.count('"target": attacker_id') >= 5)

    expected = {key: 1 for key in rows}
    if rows != expected:
        raise RuntimeError(f"pinned combat-stage ability prevention contract changed: {rows}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "contract\tvalue\n" + "".join(f"{key}\t{value}\n" for key, value in rows.items()),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
