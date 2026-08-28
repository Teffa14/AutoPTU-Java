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


def statements_in_source_order(function: ast.FunctionDef) -> list[ast.stmt]:
    nodes = [node for node in ast.walk(function) if isinstance(node, ast.stmt) and hasattr(node, "lineno")]
    return sorted(nodes, key=lambda node: (node.lineno, getattr(node, "col_offset", 0)))


def first_line(nodes: list[ast.stmt], *needles: str) -> int:
    lowered = tuple(needle.lower() for needle in needles)
    for node in nodes:
        text = norm(node)
        if all(needle in text for needle in lowered):
            return int(node.lineno)
    return -1


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    path = Path(args.source_root) / "auto_ptu" / "rules" / "battle_state.py"
    tree = ast.parse(path.read_text(encoding="utf-8"))
    function = find_function(tree, "_attempt_intercept")
    nodes = statements_in_source_order(function)
    src = norm(function)

    returns = [node for node in nodes if isinstance(node, ast.Return)]
    return_text = [norm(node) for node in returns]
    failed_check_ifs = [
        node for node in nodes
        if isinstance(node, ast.If) and "not success" in norm(node.test)
    ]
    failed_check_has_direct_return = any(
        any(isinstance(child, ast.Return) for child in node.body)
        for node in failed_check_ifs
    )

    checkpoints = {
        "candidate_sort": first_line(nodes, "sort", "distance"),
        "intercept_check": first_line(nodes, "success", "dc"),
        "failed_check": min((int(node.lineno) for node in failed_check_ifs), default=-1),
        "consume_intercept_ready": first_line(nodes, "remove_temporary_effect", "intercept_ready"),
        "consume_coaching": first_line(nodes, "remove_temporary_effect", "coaching_intercept"),
        "commit_intercept_position": first_line(nodes, "interceptor.position", "intercept_pos"),
        "melee_forced_movement": first_line(nodes, "apply_forced_movement", "interceptor_id", "target_id"),
        "commit_target_anchor": first_line(nodes, "interceptor.position", "target_pos"),
    }

    flags = {
        "failed_check_returns_from_attempt": failed_check_has_direct_return,
        "success_return_mentions_interceptor": any("interceptor_id" in text for text in return_text),
        "function_mentions_target_id": "target_id" in src,
        "function_mentions_intercept_ready": "intercept_ready" in src,
        "function_mentions_coaching_intercept": "coaching_intercept" in src,
        "function_calls_forced_movement": "apply_forced_movement" in src,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["kind\tname\tvalue"]
    lines.extend(f"flag\t{name}\t{1 if value else 0}" for name, value in flags.items())
    lines.extend(f"checkpoint\t{name}\t{line}" for name, line in checkpoints.items())
    lines.extend(f"return\t{idx}\t{text}" for idx, text in enumerate(return_text))
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")

    missing = [name for name, value in flags.items() if not value]
    if missing:
        raise RuntimeError("intercept orchestration contract missing required facts: " + ", ".join(missing))
    required_checkpoints = ("intercept_check", "failed_check", "commit_intercept_position", "melee_forced_movement", "commit_target_anchor")
    absent = [name for name in required_checkpoints if checkpoints[name] < 0]
    if absent:
        raise RuntimeError("intercept orchestration checkpoints not found: " + ", ".join(absent))


if __name__ == "__main__":
    main()
