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
    window = source[max(0, position - 500): position + 1200]

    rows = {
        "inner_focus_checks_flinch_alias_set": int("status_key in _FLINCH_STATUS_NAMES" in window),
        "inner_focus_emits_status_block": int('"effect": "status_block"' in window),
        "inner_focus_returns_before_status_write": int("return" in window),
        "flinch_application_records_applied_round": int('payload["applied_round"] = self.round' in source),
    }
    if not all(rows.values()):
        raise RuntimeError(f"pinned oracle status-application contract changed: {rows}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "contract\tvalue\n" + "".join(f"{name}\t{value}\n" for name, value in rows.items()),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
