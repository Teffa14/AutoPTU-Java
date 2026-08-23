#!/usr/bin/env python3
"""Freeze status-application/prevention contracts from the pinned Python AutoPTU source."""
from __future__ import annotations

import argparse
from pathlib import Path


def function_window(source: str, name: str, next_name: str) -> str:
    start = source.find(f"def {name}(")
    end = source.find(f"def {next_name}(", start + 1)
    if start < 0 or end < 0:
        raise RuntimeError(f"unable to locate {name} in pinned oracle")
    return source[start:end]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    source = (args.source_root / "auto_ptu" / "rules" / "battle_state.py").read_text(encoding="utf-8")
    needle = 'target.has_ability("Inner Focus")'
    position = source.find(needle)
    if position < 0:
        raise RuntimeError("Inner Focus status prevention branch not found in pinned oracle")
    window = source[max(0, position - 9000): position + 4500]

    safeguard_position = source.find('"effect": "safeguard_block"')
    if safeguard_position < 0:
        raise RuntimeError("Safeguard status prevention branch not found in pinned oracle")
    safeguard_window = source[max(0, safeguard_position - 7000): safeguard_position + 3500]
    safeguard_lower = safeguard_window.lower()

    aroma = function_window(source, "_aroma_veil_blocker", "_pastel_veil_blocker")
    pastel = function_window(source, "_pastel_veil_blocker", "_sweet_veil_blocker")
    sweet = function_window(source, "_sweet_veil_blocker", "_flower_veil_blocker")
    spatial_call_window = source[max(0, source.find('blocker = self._sweet_veil_blocker(target_id)') - 1500):
                                 source.find('blocker = self._aroma_veil_blocker(target_id)') + 1200]

    rows = {
        "inner_focus_checks_flinch_alias_set": int("status_key in _FLINCH_STATUS_NAMES" in window),
        "inner_focus_emits_status_block": int('"effect": "status_block"' in window),
        "inner_focus_returns_before_status_write": int("return" in window),
        "flinch_application_records_applied_round": int('payload["applied_round"] = self.round' in source),
        "ability_prevention_respects_suppression": int("if not abilities_suppressed" in window),
        "suppression_includes_ignore_defensive_abilities": int(
            'attacker.get_temporary_effects("ignore_defensive_abilities")' in window
        ),
        "own_tempo_blocks_confusion": int(
            'target.has_ability("Own Tempo")' in window and '{"confused", "confusion"}' in window
        ),
        "oblivious_blocks_enraged_infatuated": int(
            'target.has_ability("Oblivious")' in window and '{"enraged", "infatuated"}' in window
        ),
        "run_away_blocks_slowed_stuck_trapped": int(
            'target.has_ability("Run Away")' in window and '{"slowed", "stuck", "trapped"}' in window
        ),
        "immunity_blocks_poison_family": int(
            'target.has_ability("Immunity")' in window
            and '{"poison", "poisoned", "badly poisoned"}' in window
        ),
        "insomnia_blocks_sleep_family": int(
            'target.has_ability("Insomnia")' in window and "status_key in _SLEEP_STATUS_NAMES" in window
        ),
        "vital_spirit_blocks_sleep_family": int(
            'target.has_ability("Vital Spirit")' in window and "status_key in _SLEEP_STATUS_NAMES" in window
        ),
        "aroma_veil_normal_radius_3": int('radius = 1 if mon.has_ability("Aroma Veil [Errata]") else 3' in aroma),
        "aroma_veil_errata_radius_1": int('mon.has_ability("Aroma Veil [Errata]")' in aroma),
        "pastel_veil_radius_3": int("<= 3" in pastel),
        "sweet_veil_radius_3": int("<= 3" in sweet),
        "spatial_veil_skips_fainted_inactive": int(
            all("if mon.fainted or not mon.active" in section for section in (aroma, pastel, sweet))
        ),
        "spatial_veil_has_no_team_filter": int(
            all("team" not in section.lower() and "controller" not in section.lower() for section in (aroma, pastel, sweet))
        ),
        "aroma_veil_blocks_confused_enraged_suppressed": int(
            'status_key in {"confused", "enraged", "suppressed"}' in spatial_call_window
        ),
        "pastel_veil_blocks_poison_family": int(
            'status_key in {"poison", "poisoned", "badly poisoned"}' in spatial_call_window
        ),
        "sweet_veil_blocks_sleep_family": int(
            "status_key in _SLEEP_STATUS_NAMES" in spatial_call_window
        ),
        "spatial_veil_respects_ability_suppression": int(
            spatial_call_window.count("not abilities_suppressed") >= 3
        ),
        "safeguard_emits_status_block": int('"effect": "safeguard_block"' in safeguard_window),
        "safeguard_reads_remaining": int("remaining" in safeguard_lower),
        "safeguard_decrements_remaining_in_status_boundary": int(
            "remaining" in safeguard_lower
            and ("remaining - 1" in safeguard_lower or "remaining -= 1" in safeguard_lower)
        ),
        "safeguard_removes_when_spent_in_status_boundary": int(
            "remove_temporary_effect" in safeguard_window and "safeguard" in safeguard_lower
        ),
        "safeguard_bypassed_by_infiltrator": int("infiltrator" in safeguard_lower),
        "safeguard_bypassed_by_ignore_blessings_in_status_boundary": int("ignore_blessings" in safeguard_lower),
        "safeguard_returns_before_status_write": int("return" in safeguard_window),
    }
    expected = {
        "inner_focus_checks_flinch_alias_set": 1,
        "inner_focus_emits_status_block": 1,
        "inner_focus_returns_before_status_write": 1,
        "flinch_application_records_applied_round": 1,
        "ability_prevention_respects_suppression": 1,
        "suppression_includes_ignore_defensive_abilities": 0,
        "own_tempo_blocks_confusion": 0,
        "oblivious_blocks_enraged_infatuated": 0,
        "run_away_blocks_slowed_stuck_trapped": 0,
        "immunity_blocks_poison_family": 1,
        "insomnia_blocks_sleep_family": 1,
        "vital_spirit_blocks_sleep_family": 1,
        "aroma_veil_normal_radius_3": 1,
        "aroma_veil_errata_radius_1": 1,
        "pastel_veil_radius_3": 1,
        "sweet_veil_radius_3": 1,
        "spatial_veil_skips_fainted_inactive": 1,
        "spatial_veil_has_no_team_filter": 1,
        "aroma_veil_blocks_confused_enraged_suppressed": 1,
        "pastel_veil_blocks_poison_family": 1,
        "sweet_veil_blocks_sleep_family": 1,
        "spatial_veil_respects_ability_suppression": 1,
        "safeguard_emits_status_block": 1,
        "safeguard_reads_remaining": 1,
        "safeguard_decrements_remaining_in_status_boundary": 0,
        "safeguard_removes_when_spent_in_status_boundary": 0,
        "safeguard_bypassed_by_infiltrator": 1,
        "safeguard_bypassed_by_ignore_blessings_in_status_boundary": 0,
        "safeguard_returns_before_status_write": 1,
    }
    if rows != expected:
        raise RuntimeError(f"pinned oracle status-application contract changed: {rows}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "contract\tvalue\n" + "".join(f"{name}\t{value}\n" for name, value in rows.items()),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
