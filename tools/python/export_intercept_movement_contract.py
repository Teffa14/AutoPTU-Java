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


def is_name(node: ast.AST, name: str) -> bool:
    return isinstance(node, ast.Name) and node.id == name


def dict_literal_value(node: ast.AST, key: str):
    if not isinstance(node, ast.Dict):
        return None
    for candidate_key, candidate_value in zip(node.keys, node.values):
        if isinstance(candidate_key, ast.Constant) and candidate_key.value == key:
            if isinstance(candidate_value, ast.Constant):
                return candidate_value.value
            return None
    return None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    path = Path(args.source_root) / "auto_ptu" / "rules" / "battle_state.py"
    tree = ast.parse(path.read_text(encoding="utf-8"))
    function = find_function(tree, "_attempt_intercept")
    src = normalized(function)

    parents: dict[ast.AST, ast.AST] = {}
    for parent in ast.walk(function):
        for child in ast.iter_child_nodes(parent):
            parents[child] = parent

    forced_call = None
    for node in ast.walk(function):
        if not isinstance(node, ast.Call):
            continue
        if not isinstance(node.func, ast.Attribute) or node.func.attr != "apply_forced_movement":
            continue
        if len(node.args) < 3:
            continue
        if is_name(node.args[0], "interceptor_id") and is_name(node.args[1], "target_id"):
            forced_call = node
            break

    forced_src = normalized(forced_call) if forced_call is not None else ""
    line_commit = "interceptor.position = candidates[0]"
    final_commit = "interceptor.position = target_pos"
    forced_index = src.find(forced_src) if forced_src else -1
    line_index = src.find(line_commit)
    final_index = src.find(final_commit)

    forced_parent = parents.get(forced_call) if forced_call is not None else None
    forced_if = parents.get(forced_parent) if forced_parent is not None else None
    forced_guard = normalized(forced_if.test) if isinstance(forced_if, ast.If) else ""
    instruction = forced_call.args[2] if forced_call is not None and len(forced_call.args) >= 3 else None

    flags = {
        "commits_interceptor_to_intercept_pos": "interceptor.position = intercept_pos" in src,
        "failed_check_has_early_return": "if not success" in src and "return" in src,
        "intercept_path_does_not_consume_shift_bucket": "actiontype.shift" not in src and "action_type.shift" not in src,
        "melee_branch_uses_forced_movement": forced_call is not None,
        "melee_push_uses_interceptor_as_source": forced_call is not None and is_name(forced_call.args[0], "interceptor_id"),
        "melee_push_displaces_original_target": forced_call is not None and is_name(forced_call.args[1], "target_id"),
        "melee_push_kind_is_push": dict_literal_value(instruction, "kind") == "push",
        "melee_push_distance_is_one": dict_literal_value(instruction, "distance") == 1,
        "melee_push_guard_requires_success_and_melee": "success" in forced_guard and "kind == 'melee'" in forced_guard,
        "melee_push_return_is_ignored": isinstance(forced_parent, ast.Expr),
        "line_commit_precedes_melee_push": line_index >= 0 and forced_index > line_index,
        "melee_push_precedes_final_target_anchor_commit": forced_index >= 0 and final_index > forced_index,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(f"{key}\t{1 if value else 0}" for key, value in flags.items()) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
