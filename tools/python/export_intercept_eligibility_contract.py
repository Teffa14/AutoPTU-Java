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
    source = path.read_text(encoding="utf-8")
    tree = ast.parse(source)
    can_src = normalized(find_function(tree, "_can_intercept"))
    loyalty_src = normalized(find_function(tree, "_loyalty_allows_intercept"))

    flags = {
        "can_blocks_fainted": "actor.fainted" in can_src,
        "can_blocks_paralyzed": "has_status('paralyzed')" in can_src or 'has_status("paralyzed")' in can_src,
        "can_blocks_stuck": "has_status('stuck')" in can_src or 'has_status("stuck")' in can_src,
        "can_blocks_tripped": "has_status('tripped')" in can_src or 'has_status("tripped")' in can_src,
        "can_blocks_sleep_family": "_sleep_status_names" in can_src,
        "can_blocks_flinch_family": "_flinch_status_names" in can_src,
        "can_blocks_trapped_family": "_trapped_status_names" in can_src,
        "loyalty_reads_coaching_intercept": "coaching_intercept" in loyalty_src,
        "loyalty_missing_allows": "if loyalty is none" in loyalty_src and "return true" in loyalty_src,
        "loyalty_requires_three": "if loyalty < 3" in loyalty_src,
        "other_controller_requires_six": "if loyalty < 6 and actor.controller_id != ally.controller_id" in loyalty_src,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(f"{key}\t{1 if value else 0}" for key, value in flags.items()) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
