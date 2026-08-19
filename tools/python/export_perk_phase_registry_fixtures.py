#!/usr/bin/env python3
"""Freeze generic phase-scoped Trainer Feature/perk registry behavior from pinned Python AutoPTU."""
from __future__ import annotations

import argparse
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    hooks_path = args.source_root / "auto_ptu" / "rules" / "hooks" / "perk_hooks.py"
    hooks = hooks_path.read_text(encoding="utf-8")
    passive_path = args.source_root / "auto_ptu" / "rules" / "hooks" / "perk_effects" / "passive_combat.py"
    passive = passive_path.read_text(encoding="utf-8")

    rows = {
        "registry_is_phase_scoped": int('_PERK_HOOKS.get(phase, [])' in hooks),
        "registration_normalizes_named_perk": int('perk.lower() if perk else None' in hooks),
        "registry_preserves_registration_order": int('for perk, func in hooks:' in hooks),
        "registry_filters_by_trainer_feature": int('ctx.actor.has_trainer_feature(perk)' in hooks),
        "registry_supports_global_hooks": int('if perk:' in hooks and 'func(ctx)' in hooks),
        "end_passive_hooks_present": int(passive.count('@register_perk_hook("end"') >= 8),
        "defense_mastery_is_end_scoped": int('@register_perk_hook("end", "Defense Mastery")' in passive),
        "stat_mastery_is_end_scoped": int('@register_perk_hook("end", "Stat Mastery")' in passive),
    }
    if not all(rows.values()):
        raise RuntimeError(f"pinned oracle perk phase registry contract changed: {rows}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "contract\tvalue\n" + "".join(f"{name}\t{value}\n" for name, value in rows.items()),
        encoding="utf-8",
    )
    print(f"wrote {len(rows)} Python perk phase registry fixtures to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
