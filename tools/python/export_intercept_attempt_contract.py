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


def normalized(node: ast.AST) -> str:
    return " ".join(ast.unparse(node).lower().split())


def assignment_values(tree: ast.AST, name: str) -> set[str]:
    for node in ast.walk(tree):
        if not isinstance(node, (ast.Assign, ast.AnnAssign)):
            continue
        targets = node.targets if isinstance(node, ast.Assign) else [node.target]
        if not any(isinstance(target, ast.Name) and target.id == name for target in targets):
            continue
        value = node.value
        if isinstance(value, (ast.Set, ast.List, ast.Tuple)):
            return {
                element.value.strip().lower()
                for element in value.elts
                if isinstance(element, ast.Constant) and isinstance(element.value, str)
            }
    return set()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    path = Path(args.source_root) / "auto_ptu" / "rules" / "battle_state.py"
    tree = ast.parse(path.read_text(encoding="utf-8"))
    attempt = normalized(find_function(tree, "_attempt_intercept"))
    cannot_miss = normalized(find_function(tree, "_move_cannot_miss"))
    always_hit = assignment_values(tree, "_ALWAYS_HIT_MOVES")

    # Freeze the attempt-level gate plus the exact server-owned sources needed to
    # materialize its inputs. Eligibility, geometry, d20 checks and movement remain
    # separate contracts.
    flags = {
        "blocks_cannot_miss": ("cannot_miss" in attempt or "cannot miss" in attempt) and "return" in attempt,
        "blocks_area_attacks": ("area_kind" in attempt or "area" in attempt) and "return" in attempt,
        "distinguishes_melee_and_ranged": "melee" in attempt and "ranged" in attempt,
        "priority_interrupt_has_speed_gate": ("priority" in attempt or "interrupt" in attempt) and "speed" in attempt,
        "priority_speed_gate_is_strictly_faster": "interceptor.spec.spd <= attacker.spec.spd" in attempt,
        "attempt_calls_cannot_miss_helper": "self._move_cannot_miss(move)" in attempt,
        "priority_reads_move_priority": "move.priority > 0" in attempt,
        "interrupt_reads_range_keyword": "has_range_keyword(move, 'interrupt')" in attempt or 'has_range_keyword(move, "interrupt")' in attempt,
        "speed_gate_reads_raw_spec_spd": "interceptor.spec.spd <= attacker.spec.spd" in attempt,
        "cannot_miss_uses_exact_keywords": "move_has_keyword(move, 'cannot miss')" in cannot_miss and "move_has_keyword(move, 'never miss')" in cannot_miss,
        "cannot_miss_reads_effects_text": "move.effects_text" in cannot_miss,
        "cannot_miss_false_surrender": "false surrender" in always_hit,
        "cannot_miss_feint_attack": "feint attack" in always_hit,
        "cannot_miss_future_sight": "future sight" in always_hit,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(f"{key}\t{1 if value else 0}" for key, value in flags.items()) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
