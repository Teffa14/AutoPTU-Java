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


def first_node(nodes: list[ast.stmt], *needles: str) -> ast.stmt | None:
    lowered = tuple(needle.lower() for needle in needles)
    for node in nodes:
        text = norm(node)
        if all(needle in text for needle in lowered):
            return node
    return None


def line_of(node: ast.AST | None) -> int:
    return int(node.lineno) if node is not None and hasattr(node, "lineno") else -1


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

    parents: dict[ast.AST, ast.AST] = {}
    for parent in ast.walk(function):
        for child in ast.iter_child_nodes(parent):
            parents[child] = parent

    def guarded_by_success(node: ast.AST | None) -> bool:
        current = node
        while current is not None:
            current = parents.get(current)
            if isinstance(current, ast.If) and norm(current.test) == "success":
                return True
        return False

    returns = [node for node in nodes if isinstance(node, ast.Return)]
    return_text = [norm(node) for node in returns]
    failed_check_ifs = [
        node for node in nodes
        if isinstance(node, ast.If) and norm(node.test) in {"not success", "success is false", "success == false"}
    ]
    failed_check_has_direct_return = any(
        any(isinstance(child, ast.Return) for child in node.body)
        for node in failed_check_ifs
    )

    check_node = first_node(nodes, "success", "dc")
    ready_node = first_node(nodes, "remove_temporary_effect", "intercept_ready")
    coaching_node = first_node(nodes, "remove_temporary_effect", "coaching_intercept")
    success_branch = next(
        (node for node in nodes if isinstance(node, ast.If) and norm(node.test) == "success"),
        None,
    )

    checkpoints = {
        "candidate_sort": line_of(first_node(nodes, "sort", "distance")),
        "intercept_check": line_of(check_node),
        "consume_intercept_ready": line_of(ready_node),
        "consume_coaching": line_of(coaching_node),
        "success_branch": line_of(success_branch),
        "commit_intercept_position": line_of(first_node(nodes, "interceptor.position", "intercept_pos")),
        "melee_forced_movement": line_of(first_node(nodes, "apply_forced_movement", "interceptor_id", "target_id")),
        "commit_target_anchor": line_of(first_node(nodes, "interceptor.position", "target_pos")),
    }

    facts = {
        "failed_check_has_direct_return": failed_check_has_direct_return,
        "intercept_ready_guarded_by_success": guarded_by_success(ready_node),
        "coaching_guarded_by_success": guarded_by_success(coaching_node),
        "success_return_mentions_interceptor": any("interceptor_id" in text for text in return_text),
        "function_mentions_target_id": "target_id" in src,
        "function_mentions_intercept_ready": "intercept_ready" in src,
        "function_mentions_coaching_intercept": "coaching_intercept" in src,
        "function_calls_forced_movement": "apply_forced_movement" in src,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["kind\tname\tvalue"]
    lines.extend(f"fact\t{name}\t{1 if value else 0}" for name, value in facts.items())
    lines.extend(f"checkpoint\t{name}\t{line}" for name, line in checkpoints.items())
    lines.extend(f"return\t{idx}\t{text}" for idx, text in enumerate(return_text))
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")

    expected = {
        "failed_check_has_direct_return": False,
        "intercept_ready_guarded_by_success": False,
        "coaching_guarded_by_success": False,
        "success_return_mentions_interceptor": True,
        "function_mentions_target_id": True,
        "function_mentions_intercept_ready": True,
        "function_mentions_coaching_intercept": True,
        "function_calls_forced_movement": True,
    }
    mismatched = [name for name, value in expected.items() if facts[name] != value]
    if mismatched:
        raise RuntimeError("intercept orchestration contract changed: " + ", ".join(mismatched))

    required_checkpoints = (
        "intercept_check",
        "consume_intercept_ready",
        "consume_coaching",
        "success_branch",
        "commit_intercept_position",
        "melee_forced_movement",
        "commit_target_anchor",
    )
    absent = [name for name in required_checkpoints if checkpoints[name] < 0]
    if absent:
        raise RuntimeError("intercept orchestration checkpoints not found: " + ", ".join(absent))
    if not (
        checkpoints["intercept_check"]
        < checkpoints["consume_intercept_ready"]
        <= checkpoints["consume_coaching"]
        < checkpoints["success_branch"]
    ):
        raise RuntimeError("intercept resource/check ordering changed in Python oracle")


if __name__ == "__main__":
    main()
