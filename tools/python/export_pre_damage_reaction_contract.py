#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def function(tree: ast.AST, name: str) -> ast.FunctionDef:
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef) and node.name == name:
            return node
    raise SystemExit(f"missing function: {name}")


def source(path: Path) -> tuple[str, ast.Module]:
    text = path.read_text(encoding="utf-8")
    return text, ast.parse(text)


def segment(text: str, node: ast.AST) -> str:
    value = ast.get_source_segment(text, node)
    if value is None:
        raise SystemExit("could not recover source segment")
    return value


def contains_inside_if(text: str, fn: ast.FunctionDef, needle: str, test_text: str) -> bool:
    for node in ast.walk(fn):
        if not isinstance(node, ast.If):
            continue
        if segment(text, node.test).strip() != test_text:
            continue
        if needle in segment(text, node):
            return True
    return False


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    root = Path(args.source_root)
    interrupt_path = root / "auto_ptu/rules/hooks/abilities/pre_damage_interrupts.py"
    registry_path = root / "auto_ptu/rules/hooks/ability_hooks.py"
    battle_path = root / "auto_ptu/rules/battle_state.py"
    interrupt_text, interrupt_tree = source(interrupt_path)
    registry_text, registry_tree = source(registry_path)
    battle_text, battle_tree = source(battle_path)

    allow = segment(interrupt_text, function(interrupt_tree, "_allow_out_of_turn"))
    perception = segment(interrupt_text, function(interrupt_tree, "_perception_interrupt"))
    perception_errata = segment(interrupt_text, function(interrupt_tree, "_perception_errata_interrupt"))
    parry = segment(interrupt_text, function(interrupt_tree, "_parry_interrupt"))
    telepathy = segment(interrupt_text, function(interrupt_tree, "_telepathy_interrupt"))
    sway = segment(interrupt_text, function(interrupt_tree, "_sway_interrupt"))
    apply_hooks = segment(registry_text, function(registry_tree, "apply_ability_hooks"))
    resolve_targets_fn = function(battle_tree, "resolve_move_targets")
    resolve_targets = segment(battle_text, resolve_targets_fn)

    pre_marker = 'phase="pre_damage_interrupt"'
    post_marker = 'phase="post_result"'
    pre_index = resolve_targets.find(pre_marker)
    post_index = resolve_targets.find(post_marker)
    ordinary_resolution_index = resolve_targets.rfind("resolve_move_action(", 0, pre_index)
    shield_index = resolve_targets.find('result.get("blocked_by_shield")', pre_index)
    post_result_assignment_index = resolve_targets.find("result = post_result_ctx.result or result", post_index)
    item_bonus_index = resolve_targets.find("apply_attacker_item_damage_bonus(", post_index)
    hp_mutation_index = resolve_targets.find("_apply_damage_with_injury_rules(", post_index)
    skip_interrupt_index = resolve_targets.find("skip_interrupts = False")
    unseen_fist_index = resolve_targets.find('attacker.has_ability("Unseen Fist")', skip_interrupt_index)
    interrupt_result_index = resolve_targets.find("result = interrupt_ctx.result or result", pre_index)

    perception_first_decision = perception.find(
        '_allow_out_of_turn(ctx, ctx.defender_id, "Perception", optional=True)'
    )
    perception_ready_removal = perception.find('remove_temporary_effect("perception_ready")')
    perception_used_check = perception.find('get_temporary_effects("perception_used")')
    perception_second_decision = perception.find(
        '_allow_out_of_turn(ctx, ctx.defender_id, "Perception [Errata]", optional=True)'
    )
    perception_add_used = perception.find('add_temporary_effect("perception_used", expires_round=ctx.battle.round + 1)')

    parry_decision = parry.find('_allow_out_of_turn(ctx, ctx.defender_id, "Parry", optional=True)')
    parry_ready_removal = parry.find('remove_temporary_effect("parry_ready")')
    parry_melee_check = parry.find('targeting.normalized_target_kind(ctx.effective_move) != "melee"')
    parry_used_check = parry.find('get_temporary_effects("parry_used")')
    parry_add_used = parry.find('add_temporary_effect("parry_used", round=ctx.battle.round)')

    sway_redirect_guard = sway.find('ctx.attacker.get_temporary_effects("sway_redirect")')
    sway_melee_guard = sway.find('targeting.normalized_target_kind(ctx.effective_move) != "melee"')
    sway_status_guard = sway.find('(ctx.effective_move.category or "").strip().lower() == "status"')
    sway_used_check = sway.find('get_temporary_effects("sway_used")')
    sway_standard_check = sway.find('has_action_available(ActionType.STANDARD)')
    sway_decision = sway.find('_allow_out_of_turn(ctx, ctx.defender_id, "Sway", optional=True)')
    sway_mark_standard = sway.find('mark_action(ActionType.STANDARD, "Sway")')
    sway_add_redirect = sway.find('add_temporary_effect("sway_redirect", expires_round=ctx.battle.round)')
    sway_redirect_event = sway.find('"effect": "redirect"')
    sway_recursive_resolution = sway.find('ctx.battle.resolve_move_targets(')
    sway_remove_redirect = sway.find('remove_temporary_effect("sway_redirect")')
    sway_push_candidates = sway.find('for coord in (')
    sway_sorted_destination = sway.find('destination = sorted(candidates)[0]')
    sway_push_event = sway.find('"effect": "push"')
    sway_cancel_hit = sway.find('ctx.result["hit"] = False')

    required_payload = (
        '"actor_id": actor_id',
        '"label": label',
        '"phase": "pre_damage_interrupt"',
        '"move": str(getattr(ctx.move, "name", "") or "")',
        '"trigger_move": str(getattr(ctx.effective_move, "name", "") or "")',
        '"attacker_id": ctx.attacker_id',
        '"defender_id": ctx.defender_id',
        '"optional": optional',
    )
    rows = {
        "missing_decision_callback_allows": int(
            "return True" in allow and "should_trigger_out_of_turn" in allow
        ),
        "decision_payload_complete": int(all(token in allow for token in required_payload)),
        "perception_requires_ready": int('get_temporary_effects("perception_ready")' in perception),
        "perception_first_decision_precedes_ready_consumption": int(
            perception_first_decision >= 0 and perception_ready_removal > perception_first_decision
        ),
        "perception_checks_round_scoped_usage": int(
            perception_used_check > perception_ready_removal and 'ctx.battle.round > int(expires_round)' in perception
        ),
        "perception_uses_second_optional_decision": int(perception_second_decision > perception_used_check),
        "perception_records_next_round_expiry": int(perception_add_used > perception_second_decision),
        "perception_cancels_hit": int('ctx.result["hit"] = False' in perception),
        "perception_zeroes_damage": int('ctx.result["damage"] = 0' in perception),
        "perception_zeroes_type_multiplier": int('ctx.result["type_multiplier"] = 0.0' in perception),
        "perception_errata_requires_exact_variant": int(
            'has_ability_exact(ctx.defender, "Perception [Errata]")' in perception_errata
        ),
        "perception_errata_requires_distinct_attacker": int('ctx.attacker_id == ctx.defender_id' in perception_errata),
        "perception_errata_requires_allied_attacker": int(
            'ctx.battle._team_for(ctx.attacker_id) != ctx.battle._team_for(ctx.defender_id)' in perception_errata
        ),
        "perception_errata_rejects_status_moves": int(
            '(ctx.effective_move.category or "").strip().lower() == "status"' in perception_errata
        ),
        "perception_errata_limits_escape_to_one": int(
            'targeting.chebyshev_distance(ctx.defender.position, coord) <= 1' in perception_errata
        ),
        "perception_errata_has_no_ready_usage_bookkeeping": int(
            "perception_ready" not in perception_errata and "perception_used" not in perception_errata
        ),
        "perception_errata_cancels_hit": int('ctx.result["hit"] = False' in perception_errata),
        "perception_errata_zeroes_damage": int('ctx.result["damage"] = 0' in perception_errata),
        "perception_errata_zeroes_type_multiplier": int('ctx.result["type_multiplier"] = 0.0' in perception_errata),
        "parry_requires_ready": int('get_temporary_effects("parry_ready")' in parry),
        "parry_uses_optional_decision": int(parry_decision >= 0),
        "parry_consumes_ready_after_decision": int(parry_ready_removal > parry_decision),
        "parry_checks_melee_after_ready_consumption": int(parry_melee_check > parry_ready_removal),
        "parry_checks_round_scoped_usage": int(
            parry_used_check > parry_melee_check and 'entry.get("round") == ctx.battle.round' in parry
        ),
        "parry_records_current_round_usage": int(parry_add_used > parry_used_check),
        "parry_cancels_hit": int('ctx.result["hit"] = False' in parry),
        "parry_zeroes_damage": int('ctx.result["damage"] = 0' in parry),
        "parry_zeroes_type_multiplier": int('ctx.result["type_multiplier"] = 0.0' in parry),
        "telepathy_uses_optional_decision": int(
            '_allow_out_of_turn(ctx, ctx.defender_id, "Telepathy", optional=True)' in telepathy
        ),
        "telepathy_cancels_hit": int('ctx.result["hit"] = False' in telepathy),
        "telepathy_zeroes_damage": int('ctx.result["damage"] = 0' in telepathy),
        "telepathy_zeroes_type_multiplier": int('ctx.result["type_multiplier"] = 0.0' in telepathy),
        "sway_rejects_recursive_redirect": int(sway_redirect_guard >= 0),
        "sway_requires_melee_non_status": int(
            sway_melee_guard > sway_redirect_guard and sway_status_guard > sway_melee_guard
        ),
        "sway_checks_single_use_before_standard": int(
            sway_used_check > sway_status_guard and sway_standard_check > sway_used_check
        ),
        "sway_decides_before_spending_standard": int(
            sway_decision > sway_standard_check and sway_mark_standard > sway_decision
        ),
        "sway_records_usage_after_spending_standard": int(
            'add_temporary_effect("sway_used", count=1)' in sway
            and 'used_entry["count"] = used_count + 1' in sway
            and sway.find('add_temporary_effect("sway_used", count=1)') > sway_mark_standard
        ),
        "sway_installs_round_scoped_recursion_guard": int(
            sway_add_redirect > sway_mark_standard
            and 'expires_round=ctx.battle.round' in sway
        ),
        "sway_emits_redirect_before_recursive_resolution": int(
            sway_redirect_event > sway_add_redirect and sway_recursive_resolution > sway_redirect_event
        ),
        "sway_redirects_attacker_into_own_move": int(
            'attacker_id=ctx.attacker_id' in sway
            and 'target_id=ctx.attacker_id' in sway
            and 'target_position=ctx.attacker.position' in sway
        ),
        "sway_swallows_recursive_resolution_errors": int(
            'except Exception:' in sway and 'pass' in sway
        ),
        "sway_clears_recursion_guard_after_resolution": int(
            sway_remove_redirect > sway_recursive_resolution
            and 'while ctx.attacker.remove_temporary_effect("sway_redirect")' in sway
        ),
        "sway_pushes_from_eight_neighbor_candidates": int(
            sway_push_candidates > sway_remove_redirect
            and '(x + 1, y + 1)' in sway
            and '(x - 1, y - 1)' in sway
        ),
        "sway_push_filters_bounds_blockers_and_live_occupancy": int(
            'ctx.battle.grid.in_bounds(coord)' in sway
            and 'coord in ctx.battle.grid.blockers' in sway
            and 'pid not in {ctx.attacker_id, ctx.defender_id}' in sway
            and 'mon.hp > 0' in sway
        ),
        "sway_push_chooses_lexicographically_first_candidate": int(
            sway_sorted_destination > sway_push_candidates
        ),
        "sway_emits_push_before_cancelling_original_hit": int(
            sway_push_event > sway_sorted_destination and sway_cancel_hit > sway_push_event
        ),
        "sway_cancels_original_hit": int('ctx.result["hit"] = False' in sway),
        "sway_zeroes_original_damage": int('ctx.result["damage"] = 0' in sway),
        "sway_zeroes_original_type_multiplier": int('ctx.result["type_multiplier"] = 0.0' in sway),
        "ability_registry_continues_after_hook": int(
            "func(ctx)" in apply_hooks and "for ability, holder, func in hooks:" in apply_hooks
        ),
        "pre_damage_interrupt_only_runs_after_hit": int(
            contains_inside_if(battle_text, resolve_targets_fn, pre_marker, "hit")
        ),
        "ordinary_move_resolution_precedes_pre_damage_interrupt": int(
            ordinary_resolution_index >= 0 and ordinary_resolution_index < pre_index
        ),
        "pre_damage_interrupt_precedes_post_result": int(pre_index >= 0 and post_index > pre_index),
        "interrupt_result_replaces_result_before_post_result": int(
            interrupt_result_index > pre_index and interrupt_result_index < post_index
        ),
        "shield_block_check_sits_between_pre_damage_and_post_result": int(
            shield_index > pre_index and shield_index < post_index
        ),
        "interrupt_suppression_precedes_pre_damage_interrupt": int(
            skip_interrupt_index >= 0 and unseen_fist_index > skip_interrupt_index and unseen_fist_index < pre_index
        ),
        "post_result_precedes_attacker_item_damage_bonus": int(
            post_result_assignment_index > post_index and item_bonus_index > post_result_assignment_index
        ),
        "post_result_precedes_hp_mutation": int(
            post_result_assignment_index > post_index and hp_mutation_index > post_result_assignment_index
        ),
    }
    if not all(rows.values()):
        raise SystemExit(f"unexpected pre-damage reaction contract: {rows}")

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        "property\tvalue\n" + "".join(f"{k}\t{v}\n" for k, v in rows.items()),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
