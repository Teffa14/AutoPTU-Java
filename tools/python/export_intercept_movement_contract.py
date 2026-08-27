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
    function = find_function(tree, "_attempt_intercept")
    src = normalized(function)

    flags = {
        "commits_interceptor_to_intercept_pos": "interceptor.position = intercept_pos" in src,
        "failed_check_has_early_return": "if not success" in src and "return" in src,
        "intercept_path_does_not_consume_shift_bucket": "actiontype.shift" not in src and "action_type.shift" not in src,
        "melee_branch_uses_forced_movement": "apply_forced_movement" in src and "push" in src,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(f"{key}\t{1 if value else 0}" for key, value in flags.items()) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
