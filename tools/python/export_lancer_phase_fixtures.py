#!/usr/bin/env python3
"""Freeze Lancer END phase behavior from the pinned Python AutoPTU source."""
from __future__ import annotations

import argparse
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_path = args.source_root / "auto_ptu" / "rules" / "hooks" / "abilities" / "phase_effects.py"
    source = source_path.read_text(encoding="utf-8")
    marker = '@register_phase_hook("end", ability="Lancer")'
    start = source.find(marker)
    if start < 0:
        raise RuntimeError("Lancer END phase registration not found in pinned oracle")
    next_registration = source.find("@register_phase_hook", start + len(marker))
    window = source[start: next_registration if next_registration >= 0 else len(source)]

    rows = {
        "registered_at_end": int(marker in window),
        "reads_lancer_shift": int('get_temporary_effects("lancer_shift")' in window),
        "drops_stale_round_entries": int('entry.get("round") != battle.round' in window and 'remove_temporary_effect("lancer_shift")' in window),
        "requires_shift_distance_three": int("shifted_distance >= 3" in window),
        "grants_crit_range_bonus_three": int('"crit_range_bonus"' in window and 'bonus=3' in window and '"effect": "crit_range"' in window),
        "crit_bonus_expires_next_round": int('expires_round=battle.round + 1' in window),
        "hold_position_checks_shift_action": int('getattr(key, "value", "") == "shift"' in window),
        "grants_damage_reduction_five": int('"damage_reduction"' in window and 'amount=5' in window and '"effect": "damage_reduction"' in window),
    }
    if not all(rows.values()):
        raise RuntimeError(f"pinned oracle Lancer phase contract changed: {rows}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "contract\tvalue\n" + "".join(f"{name}\t{value}\n" for name, value in rows.items()),
        encoding="utf-8",
    )
    print(f"wrote {len(rows)} Python Lancer phase fixtures to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
