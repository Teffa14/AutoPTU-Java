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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    path = Path(args.source_root) / "auto_ptu" / "rules" / "battle_state.py"
    tree = ast.parse(path.read_text(encoding="utf-8"))
    fn = find_function(tree, "_attempt_intercept")
    src = normalized(fn)

    # Freeze only the attempt-level gate. Eligibility of individual interceptors,
    # candidate geometry, the d20 check, and movement each have separate contracts.
    flags = {
        "blocks_cannot_miss": ("cannot_miss" in src or "cannot miss" in src) and "return" in src,
        "blocks_area_attacks": ("area_kind" in src or "area" in src) and "return" in src,
        "distinguishes_melee_and_ranged": "melee" in src and "ranged" in src,
        "priority_interrupt_has_speed_gate": ("priority" in src or "interrupt" in src) and "speed" in src,
        "priority_speed_gate_is_strictly_faster": ("> attacker" in src or "<= interceptor" in src or "interceptor_speed <=" in src or "attacker_speed >=" in src),
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(f"{key}\t{1 if value else 0}" for key, value in flags.items()) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
