#!/usr/bin/env python3
"""Export the pinned Python delayed-hit execution and target-geometry contract.

Due delayed hits enter BattleState.resolve_move_targets, which then re-enters the
ordinary move-action resolver. Both stored target id and target position are
forwarded unchanged. If target_id still resolves, ordinary target resolution uses
the defender's current position; target_position is the fallback when there is no
defender. Area geometry is recomputed at maturity through affected_tiles, LoS
filtering and footprint overlap rather than being frozen when the hit is scheduled.
The effective target collector excludes combatants without positive HP but does not
apply a generic active-state filter at this stage.
"""

from __future__ import annotations

import argparse
import ast
import inspect
import sys
import textwrap
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args()


def function_tree(fn) -> ast.AST:
    return ast.parse(textwrap.dedent(inspect.getsource(fn)))


def call_name(node: ast.Call) -> str:
    target = node.func
    if isinstance(target, ast.Name):
        return target.id
    if isinstance(target, ast.Attribute):
        return target.attr
    return ""


def call_names(fn) -> list[str]:
    return [
        name
        for node in ast.walk(function_tree(fn))
        if isinstance(node, ast.Call)
        if (name := call_name(node))
    ]


def keyword_names_for_call(fn, expected_call: str) -> set[str]:
    names: set[str] = set()
    for node in ast.walk(function_tree(fn)):
        if not isinstance(node, ast.Call) or call_name(node) != expected_call:
            continue
        names.update(keyword.arg for keyword in node.keywords if keyword.arg)
    return names


def target_position_rewrites_move_to_tile(fn) -> bool:
    tree = function_tree(fn)
    for node in ast.walk(tree):
        if not isinstance(node, ast.Assign):
            continue
        if not isinstance(node.value, ast.Constant) or node.value.value != "Tile":
            continue
        for target in node.targets:
            if isinstance(target, ast.Attribute) and target.attr == "target":
                return True
    return False


def resolved_target_position_contract(fn) -> tuple[bool, bool]:
    """Detect: resolved_target_pos = defender.position if defender else target_position.

    The two returned flags deliberately freeze both branches independently: a live
    defender wins when present, and the stored target_position is the fallback when
    the stored target id no longer resolves.
    """
    for node in ast.walk(function_tree(fn)):
        if not isinstance(node, ast.Assign):
            continue
        if not any(isinstance(target, ast.Name) and target.id == "resolved_target_pos" for target in node.targets):
            continue
        value = node.value
        if not isinstance(value, ast.IfExp):
            continue
        if not isinstance(value.test, ast.Name) or value.test.id != "defender":
            continue
        uses_live_defender = (
            isinstance(value.body, ast.Attribute)
            and isinstance(value.body.value, ast.Name)
            and value.body.value.id == "defender"
            and value.body.attr == "position"
        )
        uses_stored_fallback = isinstance(value.orelse, ast.Name) and value.orelse.id == "target_position"
        return uses_live_defender, uses_stored_fallback
    return False, False


def target_id_is_prioritized_for_area_resolution(fn) -> bool:
    tree = function_tree(fn)
    for node in ast.walk(tree):
        if not isinstance(node, ast.If):
            continue
        if not isinstance(node.test, ast.Name) or node.test.id != "target_id":
            continue
        for child in ast.walk(node):
            if not isinstance(child, ast.Call) or call_name(child) != "append":
                continue
            if len(child.args) == 1 and isinstance(child.args[0], ast.Name) and child.args[0].id == "target_id":
                return True
    return False


def area_target_eligibility_contract(fn) -> tuple[bool, bool]:
    """Freeze the generic area collector's HP and active-state eligibility rules.

    Python builds ``prioritized`` and then resolves ``state = self.pokemon.get(cid)``.
    Its direct guard excludes missing/non-positive-HP states before footprint overlap.
    There is intentionally no generic ``active`` check in this collector. Keeping both
    facts explicit prevents Java from either hitting fainted combatants or over-filtering
    inactive combatants in a path where the Python oracle still includes them.
    """
    for node in ast.walk(function_tree(fn)):
        if not isinstance(node, ast.For):
            continue
        if not isinstance(node.target, ast.Name) or node.target.id != "cid":
            continue
        if not isinstance(node.iter, ast.Name) or node.iter.id != "prioritized":
            continue

        saw_state_lookup = False
        filters_nonpositive_hp = False
        filters_inactive = False
        for statement in node.body:
            if isinstance(statement, ast.Assign):
                for target in statement.targets:
                    if isinstance(target, ast.Name) and target.id == "state":
                        if isinstance(statement.value, ast.Call) and call_name(statement.value) == "get":
                            saw_state_lookup = True
            if not saw_state_lookup:
                continue
            if isinstance(statement, ast.If):
                test_text = ast.unparse(statement.test)
                if "state.hp is None" in test_text and "state.hp <= 0" in test_text:
                    filters_nonpositive_hp = True
                if "state.active" in test_text:
                    filters_inactive = True
            if isinstance(statement, ast.If):
                if any(
                    isinstance(child, ast.Call) and call_name(child) == "_footprint_overlaps_tiles"
                    for child in ast.walk(statement)
                ):
                    break
        return filters_nonpositive_hp, filters_inactive
    return False, False


def main() -> int:
    args = parse_args()
    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.rules.battle_state import BattleState
    from auto_ptu.rules.hooks.move_effect_tools import resolve_delayed_hits

    delayed_calls = call_names(resolve_delayed_hits)
    target_calls = call_names(BattleState.resolve_move_targets)
    forwarded = keyword_names_for_call(resolve_delayed_hits, "resolve_move_targets")
    delayed_rewrites_tile = target_position_rewrites_move_to_tile(resolve_delayed_hits)
    target_resolution_rewrites_tile = target_position_rewrites_move_to_tile(BattleState.resolve_move_targets)
    uses_live_defender_position, uses_stored_position_fallback = resolved_target_position_contract(
        BattleState.resolve_move_targets
    )
    area_uses_affected_tiles = "affected_tiles" in target_calls
    area_uses_footprint_overlap = "_footprint_overlaps_tiles" in target_calls
    area_uses_los = "line_of_sight_clear" in target_calls
    target_id_priority = target_id_is_prioritized_for_area_resolution(BattleState.resolve_move_targets)
    filters_nonpositive_hp, filters_inactive = area_target_eligibility_contract(BattleState.resolve_move_targets)

    print("--- PINNED resolve_delayed_hits ---")
    print(inspect.getsource(resolve_delayed_hits))
    print("--- PINNED BattleState.resolve_move_targets ---")
    print(inspect.getsource(BattleState.resolve_move_targets))

    # Fail loudly if Python changes this execution or target-geometry boundary.
    assert "resolve_move_targets" in delayed_calls
    assert "resolve_move_action" not in delayed_calls
    assert "target_id" in forwarded
    assert "target_position" in forwarded
    assert "resolve_move_action" in target_calls
    assert not delayed_rewrites_tile
    assert not target_resolution_rewrites_tile
    assert uses_live_defender_position
    assert uses_stored_position_fallback
    assert area_uses_affected_tiles
    assert area_uses_footprint_overlap
    assert area_uses_los
    assert target_id_priority
    assert filters_nonpositive_hp
    assert not filters_inactive

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\t".join(
            [
                "TARGET_RESOLUTION",
                "1" if "resolve_move_targets" in delayed_calls else "0",
                "1" if "resolve_move_action" in delayed_calls else "0",
                "1" if "target_id" in forwarded else "0",
                "1" if "target_position" in forwarded else "0",
                "1" if "resolve_move_action" in target_calls else "0",
                "1" if (delayed_rewrites_tile or target_resolution_rewrites_tile) else "0",
                "1" if uses_live_defender_position else "0",
                "1" if area_uses_affected_tiles else "0",
                "1" if area_uses_footprint_overlap else "0",
                "1" if area_uses_los else "0",
                "1" if target_id_priority else "0",
                "1" if uses_stored_position_fallback else "0",
                "1" if filters_nonpositive_hp else "0",
                "1" if filters_inactive else "0",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
