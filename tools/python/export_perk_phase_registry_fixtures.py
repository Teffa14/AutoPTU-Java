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
        "links_resolve_trainer_from_actor_controller": int(
            'get(getattr(actor, "controller_id", ""))' in passive
        ),
        "links_require_trainer_ap": int(
            'int(getattr(trainer, "ap", 0) or 0) < 1' in passive
        ),
        "links_spend_trainer_ap": int(
            'trainer.ap = int(getattr(trainer, "ap", 0) or 0) - 1' in passive
        ),
        "links_read_combat_stage": int(
            'getattr(actor, "combat_stages", {}).get(stat, 0)' in passive
        ),
        "links_require_nonpositive_stage": int(
            'if current > 0:' in passive
        ),
        "links_raise_stage_by_one_with_cap": int(
            'actor.combat_stages[stat] = min(6, current + 1)' in passive
        ),
        "links_emit_raise_cs_event": int(
            '"effect": "raise_cs"' in passive
        ),
        "links_event_reports_stat": int(
            '"stat": stat' in passive
        ),
        "links_event_reports_amount": int(
            '"amount": 1' in passive
        ),
        "links_event_reports_ap_spent": int(
            '"ap_spent": 1' in passive
        ),
        "links_event_reports_phase": int(
            '"phase": ctx.phase' in passive
        ),
        "fixed_links_are_end_scoped": int(all(
            f'@register_perk_hook("end", "{feature}")' in passive
            for feature in (
                "Attack Link",
                "Defense Link",
                "Special Attack Link",
                "Special Defense Link",
                "Speed Link",
            )
        )),
        "perk_filter_is_actor_feature_owned": int(
            'ctx.actor.has_trainer_feature(perk)' in hooks
        ),
        "defense_mastery_skips_fainted_actor": int(
            'if getattr(actor, "fainted", False):' in passive
        ),
        "defense_mastery_reads_shifted_this_turn": int(
            'actor.get_temporary_effects("shifted_this_turn")' in passive
        ),
        "defense_mastery_requires_current_round_shift": int(
            '== round_no' in passive and 'for entry in actor.get_temporary_effects("shifted_this_turn")' in passive
        ),
        "defense_mastery_grants_five_damage_reduction": int(
            '"damage_reduction",\n        amount=5,' in passive
        ),
        "defense_mastery_expires_next_round": int(
            'expires_round=round_no + 1' in passive
        ),
        "defense_mastery_is_non_consuming": int(
            'consume=False' in passive
        ),
        "defense_mastery_event_keeps_trainer": int(
            '"trainer": actor.controller_id' in passive
        ),
        "defense_mastery_event_reports_amount": int(
            '"effect": "damage_reduction"' in passive and '"amount": 5' in passive
        ),
        "defense_mastery_event_reports_phase": int(
            '"phase": ctx.phase' in passive
        ),
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
