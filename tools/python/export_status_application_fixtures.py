#!/usr/bin/env python3
"""Freeze status-application/prevention contracts from the pinned Python AutoPTU source."""
from __future__ import annotations

import argparse
from pathlib import Path


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

    aroma_matches: list[str] = []
    for path in sorted((args.source_root / "auto_ptu").rglob("*.py")):
        text = path.read_text(encoding="utf-8", errors="ignore")
        cursor = 0
        while True:
            index = text.find("Aroma Veil", cursor)
            if index < 0:
                break
            line = text.count("\n", 0, index) + 1
            snippet = text[max(0, index - 180): index + 260].replace("\n", "\\n")
            aroma_matches.append(f"{path.relative_to(args.source_root)}:{line}:{snippet}")
            cursor = index + len("Aroma Veil")
    if not aroma_matches:
        raise RuntimeError("Aroma Veil implementation not found anywhere under pinned auto_ptu Python package")
    raise RuntimeError("Aroma Veil implementation locations: " + " || ".join(aroma_matches[:20]))

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
