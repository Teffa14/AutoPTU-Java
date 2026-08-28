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


def assignment_parts(node: ast.stmt) -> tuple[list[ast.expr], ast.expr] | None:
    if isinstance(node, ast.Assign):
        return list(node.targets), node.value
    if isinstance(node, ast.AnnAssign) and node.value is not None:
        return [node.target], node.value
    return None


def target_is_name(target: ast.expr, name: str) -> bool:
    return isinstance(target, ast.Name) and target.id == name


def assignment_to_name(nodes: list[ast.stmt], name: str) -> ast.stmt | None:
    for node in nodes:
        parts = assignment_parts(node)
        if parts is None:
            continue
        targets, _value = parts
        if any(target_is_name(target, name) for target in targets):
            return node
    return None


def call_from_statement(node: ast.stmt) -> ast.Call | None:
    if isinstance(node, ast.Expr) and isinstance(node.value, ast.Call):
        return node.value
    return None


def call_method_name(call: ast.Call) -> str | None:
    if isinstance(call.func, ast.Attribute):
        return call.func.attr
    if isinstance(call.func, ast.Name):
        return call.func.id
    return None


def call_receiver_name(call: ast.Call) -> str | None:
    if isinstance(call.func, ast.Attribute) and isinstance(call.func.value, ast.Name):
        return call.func.value.id
    return None


def call_has_string_arg(call: ast.Call, value: str) -> bool:
    expected = value.lower()
    return any(
        isinstance(arg, ast.Constant)
        and isinstance(arg.value, str)
        and arg.value.lower() == expected
        for arg in call.args
    )


def first_method_call(
    nodes: list[ast.stmt],
    method: str,
    *,
    receiver: str | None = None,
    string_arg: str | None = None,
) -> ast.stmt | None:
    for node in nodes:
        call = call_from_statement(node)
        if call is None or call_method_name(call) != method:
            continue
        if receiver is not None and call_receiver_name(call) != receiver:
            continue
        if string_arg is not None and not call_has_string_arg(call, string_arg):
            continue
        return node
    return None


def position_assignment(nodes: list[ast.stmt], value_predicate) -> ast.stmt | None:
    for node in nodes:
        parts = assignment_parts(node)
        if parts is None:
            continue
        targets, value = parts
        if not any(isinstance(target, ast.Attribute) and target.attr == "position" for target in targets):
            continue
        if value_predicate(value):
            return node
    return None


def is_candidates_zero(value: ast.expr) -> bool:
    if not isinstance(value, ast.Subscript) or not isinstance(value.value, ast.Name) or value.value.id != "candidates":
        return False
    index = value.slice
    return isinstance(index, ast.Constant) and index.value == 0


def is_name(value: ast.expr, name: str) -> bool:
    return isinstance(value, ast.Name) and value.id == name


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

    tree_parents: dict[ast.AST, ast.AST] = {}
    for parent in ast.walk(tree):
        for child in ast.iter_child_nodes(parent):
            tree_parents[child] = parent

    def guarded_by_exact_success(node: ast.AST | None) -> bool:
        current = node
        while current is not None:
            current = parents.get(current)
            if isinstance(current, ast.If) and isinstance(current.test, ast.Name) and current.test.id == "success":
                return True
        return False

    def guarded_by_success_and_melee(node: ast.AST | None) -> bool:
        current = node
        while current is not None:
            current = parents.get(current)
            if not isinstance(current, ast.If) or not isinstance(current.test, ast.BoolOp):
                continue
            terms = [norm(value) for value in current.test.values]
            if isinstance(current.test.op, ast.And) and "success" in terms and "kind == 'melee'" in terms:
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

    check_node = assignment_to_name(nodes, "success")
    ready_node = first_method_call(nodes, "remove_temporary_effect", string_arg="intercept_ready")
    coaching_node = first_method_call(nodes, "remove_temporary_effect", string_arg="coaching_intercept")
    success_branch = next(
        (
            node for node in nodes
            if isinstance(node, ast.If) and isinstance(node.test, ast.Name) and node.test.id == "success"
        ),
        None,
    )
    candidate_sort = first_method_call(nodes, "sort", receiver="interceptors")
    candidate_position = position_assignment(nodes, is_candidates_zero)
    forced_movement = first_method_call(nodes, "apply_forced_movement")
    target_anchor = position_assignment(nodes, lambda value: is_name(value, "target_pos"))

    intercept_calls = [
        node for node in ast.walk(tree)
        if isinstance(node, ast.Call) and call_method_name(node) == "_attempt_intercept"
    ]
    target_replacement_calls: list[ast.Call] = []
    for call in intercept_calls:
        parent = tree_parents.get(call)
        if isinstance(parent, ast.Assign) and parent.value is call:
            if any(target_is_name(target, "target_id") for target in parent.targets):
                target_replacement_calls.append(call)
        elif isinstance(parent, ast.AnnAssign) and parent.value is call and target_is_name(parent.target, "target_id"):
            target_replacement_calls.append(call)

    checkpoints = {
        "candidate_sort": line_of(candidate_sort),
        "intercept_check": line_of(check_node),
        "consume_intercept_ready": line_of(ready_node),
        "consume_coaching": line_of(coaching_node),
        "success_branch": line_of(success_branch),
        "commit_candidate_position": line_of(candidate_position),
        "melee_forced_movement": line_of(forced_movement),
        "commit_target_anchor": line_of(target_anchor),
        "target_replacement_call": min((line_of(call) for call in target_replacement_calls), default=-1),
    }

    facts = {
        "failed_check_has_direct_return": failed_check_has_direct_return,
        "intercept_ready_guarded_by_success": guarded_by_exact_success(ready_node),
        "coaching_guarded_by_success": guarded_by_exact_success(coaching_node),
        "candidate_position_guarded_by_success": guarded_by_exact_success(candidate_position),
        "target_anchor_guarded_by_success": guarded_by_exact_success(target_anchor),
        "melee_forced_movement_guarded_by_success_and_melee": guarded_by_success_and_melee(forced_movement),
        "success_return_mentions_interceptor": any("interceptor_id if success else target_id" in text for text in return_text),
        "function_mentions_target_id": "target_id" in src,
        "function_mentions_intercept_ready": "intercept_ready" in src,
        "function_mentions_coaching_intercept": "coaching_intercept" in src,
        "function_calls_forced_movement": "apply_forced_movement" in src,
        "call_site_assigns_result_to_target_id": bool(target_replacement_calls),
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
        "candidate_position_guarded_by_success": False,
        "target_anchor_guarded_by_success": False,
        "melee_forced_movement_guarded_by_success_and_melee": True,
        "success_return_mentions_interceptor": True,
        "function_mentions_target_id": True,
        "function_mentions_intercept_ready": True,
        "function_mentions_coaching_intercept": True,
        "function_calls_forced_movement": True,
        "call_site_assigns_result_to_target_id": True,
    }
    mismatched = [name for name, value in expected.items() if facts[name] != value]
    if mismatched:
        raise RuntimeError("intercept orchestration contract changed: " + ", ".join(mismatched))

    required_checkpoints = (
        "candidate_sort",
        "intercept_check",
        "consume_intercept_ready",
        "consume_coaching",
        "success_branch",
        "commit_candidate_position",
        "melee_forced_movement",
        "commit_target_anchor",
        "target_replacement_call",
    )
    absent = [name for name in required_checkpoints if checkpoints[name] < 0]
    if absent:
        raise RuntimeError("intercept orchestration checkpoints not found: " + ", ".join(absent))

    if not (
        checkpoints["candidate_sort"]
        < checkpoints["intercept_check"]
        < checkpoints["consume_intercept_ready"]
        <= checkpoints["consume_coaching"]
        < checkpoints["success_branch"]
        < checkpoints["commit_candidate_position"]
        < checkpoints["melee_forced_movement"]
        < checkpoints["commit_target_anchor"]
    ):
        raise RuntimeError("intercept orchestration ordering changed in Python oracle")


if __name__ == "__main__":
    main()
