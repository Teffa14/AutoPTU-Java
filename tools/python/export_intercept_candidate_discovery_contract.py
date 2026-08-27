#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def find_function(tree: ast.AST, name: str) -> ast.FunctionDef:
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef) and node.name == name:
            return node
    raise RuntimeError(f"missing Python function: {name}")


def norm(node: ast.AST) -> str:
    return " ".join(ast.unparse(node).lower().split())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    path = Path(args.source_root) / "auto_ptu" / "rules" / "battle_state.py"
    tree = ast.parse(path.read_text(encoding="utf-8"))
    src = norm(find_function(tree, "_attempt_intercept"))

    flags = {
        "kind_melee_else_ranged": "kind = 'melee' if targeting.normalized_target_kind(move) == 'melee' else 'ranged'" in src,
        "attacker_no_intercept_precedes_candidates": "attacker.get_temporary_effects('no_intercept')" in src and src.index("attacker.get_temporary_effects('no_intercept')") < src.index("interceptors: list"),
        "no_intercept_expires_strictly_after_round": "self.round > int(expires_round)" in src,
        "no_intercept_removes_expired": "attacker.remove_temporary_effect('no_intercept')" in src,
        "candidate_skips_target": "if pid == target_id" in src,
        "candidate_requires_positive_hp": "mon.hp is none or mon.hp <= 0" in src,
        "candidate_requires_same_team": "self._team_for(pid) != self._team_for(target_id)" in src,
        "weaponize_requires_ability": "mon.has_ability('weaponize')" in src,
        "weaponize_requires_living_weapon": "mon.has_capability('living weapon')" in src,
        "weaponize_controller_is_target": "mon.controller_id == target_id" in src,
        "weaponize_continues_after_append": "source': 'weaponize'" in src and "continue" in src[src.index("source': 'weaponize'"):],
        "ready_matches_ally": "entry.get('ally') == target_id" in src,
        "ready_matches_kind": "entry.get('intercept_kind') == kind" in src,
        "sentinel_expires_strictly_after_round": "mon.get_temporary_effects('sentinel_stance')" in src and "self.round > int(expires_round)" in src,
        "sentinel_removes_expired": "mon.remove_temporary_effect('sentinel_stance')" in src,
        "sentinel_requires_base_or_extra_shift": "mon.has_action_available(actiontype.shift)" in src and "self._extra_action_count(mon, actiontype.shift)" in src,
        "sentinel_marks_uses_shift": "'uses_shift': true" in src,
        "sources_require_can_intercept": "self._can_intercept(mon)" in src,
        "sources_require_loyalty": "self._loyalty_allows_intercept(mon, target)" in src,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("key\tvalue\n" + "".join(f"{key}\t{1 if value else 0}\n" for key, value in flags.items()), encoding="utf-8")


if __name__ == "__main__":
    main()
