#!/usr/bin/env python3
"""Freeze Mirror Armor combat-stage reflection from the pinned Python oracle."""
from __future__ import annotations

import argparse
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = (args.source_root / "auto_ptu" / "rules" / "battle_state.py").read_text(encoding="utf-8")
    marker = 'target.has_ability("Mirror Armor")'
    pos = source.find(marker)
    if pos < 0:
        raise RuntimeError("Mirror Armor combat-stage branch not found in pinned oracle")
    window = source[max(0, pos - 900): pos + 2600]

    recurse = window.find("_apply_combat_stage(")
    reflect = window.find('"effect": "reflect"')
    rows = {
        "negative_only": int("delta < 0" in window),
        "external_only": int("target_id != attacker_id" in window),
        "suppression_guarded": int("not abilities_suppressed" in window),
        "mirror_skip_guarded": int("not skip_mirror_armor" in window),
        "emits_reflect": int(reflect >= 0),
        "reenters_combat_stage": int(recurse >= 0),
        "suppresses_recursive_mirror": int("skip_mirror_armor=True" in window),
        "preserves_stat": int(recurse >= 0 and "stat" in window[recurse:recurse + 900]),
        "preserves_delta": int(recurse >= 0 and "delta" in window[recurse:recurse + 900]),
        "reflect_event_before_reentry": int(reflect >= 0 and recurse >= 0 and reflect < recurse),
        "event_actor_is_target": int('"actor": target_id' in window),
        "event_target_is_attacker": int('"target": attacker_id' in window),
        "blocks_original_after_reflection": int(recurse >= 0 and "return" in window[recurse:recurse + 1200]),
    }
    expected = {key: 1 for key in rows}
    if rows != expected:
        raise RuntimeError(f"pinned Mirror Armor contract changed: {rows}\nwindow:\n{window}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "contract\tvalue\n" + "".join(f"{key}\t{value}\n" for key, value in rows.items()),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
