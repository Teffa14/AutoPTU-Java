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
    function = find_function(tree, "_attempt_intercept")
    src = norm(function)

    statements: list[str] = []
    needles = (
        "intercept_ready",
        "coaching_intercept",
        "sentinel_stance",
        "remove_temporary_effect",
        "consume_action",
        "extra_action",
        "actiontype.shift",
        "action_type.shift",
    )
    for node in ast.walk(function):
        if not isinstance(node, (ast.Assign, ast.AnnAssign, ast.AugAssign, ast.Expr, ast.If)):
            continue
        text = norm(node)
        if any(needle in text for needle in needles):
            statements.append(text)

    flags = {
        "mentions_intercept_ready": "intercept_ready" in src,
        "mentions_coaching_intercept": "coaching_intercept" in src,
        "mentions_sentinel_stance": "sentinel_stance" in src,
        "removes_intercept_ready": "remove_temporary_effect('intercept_ready')" in src or 'remove_temporary_effect("intercept_ready")' in src,
        "removes_coaching_intercept": "remove_temporary_effect('coaching_intercept')" in src or 'remove_temporary_effect("coaching_intercept")' in src,
        "removes_sentinel_stance": "remove_temporary_effect('sentinel_stance')" in src or 'remove_temporary_effect("sentinel_stance")' in src,
        "consumes_shift_action": "consume_action(actiontype.shift" in src or "consume_action(action_type.shift" in src,
        "calls_extra_action_consumer": "consume_extra_action" in src or "_consume_extra_action" in src,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["key\tvalue"]
    lines.extend(f"{key}\t{1 if value else 0}" for key, value in flags.items())
    lines.append("statement\ttext")
    lines.extend(f"statement\t{text}" for text in statements)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
