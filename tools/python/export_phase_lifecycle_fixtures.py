#!/usr/bin/env python3
"""Extract PhaseController.advance_phase contracts from the pinned Python oracle."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def find_method(tree: ast.Module, name: str) -> ast.FunctionDef:
    for node in tree.body:
        if isinstance(node, ast.ClassDef) and node.name == "PhaseController":
            for statement in node.body:
                if isinstance(statement, ast.FunctionDef) and statement.name == name:
                    return statement
    raise RuntimeError(f"PhaseController.{name} not found")


def logs_event_type(method: ast.FunctionDef, event_type: str) -> bool:
    for node in ast.walk(method):
        if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Attribute):
            continue
        if node.func.attr != "log_event" or not node.args or not isinstance(node.args[0], ast.Dict):
            continue
        for key, value in zip(node.args[0].keys, node.args[0].values):
            if isinstance(key, ast.Constant) and key.value == "type" and isinstance(value, ast.Constant) and value.value == event_type:
                return True
    return False


def calls_attr(method: ast.FunctionDef, attr: str) -> bool:
    return any(
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == attr
        for node in ast.walk(method)
    )


def dispatches_trigger(method: ast.FunctionDef, trigger: str) -> bool:
    return any(
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == "trigger"
        and node.args
        and isinstance(node.args[0], ast.Constant)
        and node.args[0].value == trigger
        for node in ast.walk(method)
    )


def requires_current_actor(method: ast.FunctionDef) -> bool:
    return any(
        isinstance(node, ast.If)
        and isinstance(node.test, ast.Compare)
        and any(isinstance(child, ast.Attribute) and child.attr == "current_actor_id" for child in ast.walk(node.test))
        and any(isinstance(child, ast.Raise) for child in ast.walk(node))
        for node in method.body
    )


def terminal_end_return(method: ast.FunctionDef) -> bool:
    return any(
        isinstance(node, ast.If)
        and any(isinstance(child, ast.Compare) for child in ast.walk(node.test))
        and any(isinstance(child, ast.Return) for child in node.body)
        for node in method.body
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    path = args.source_root.resolve() / "auto_ptu" / "rules" / "controllers" / "phase_controller.py"
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    method = find_method(tree, "advance_phase")

    fixtures = [
        ("requires_current_actor", int(requires_current_actor(method))),
        ("logs_phase_event", int(logs_event_type(method, "phase"))),
        ("dispatches_phase_change", int(dispatches_trigger(method, "phase_change"))),
        ("runs_status_phase_effects", int(calls_attr(method, "run_phase_effects"))),
        ("consumes_pending_status_skip", int(calls_attr(method, "consume_pending_status_skip"))),
        ("end_phase_is_terminal", int(terminal_end_return(method))),
    ]

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(f"{name}\t{value}" for name, value in fixtures) + "\n", encoding="utf-8")
    print(f"wrote {len(fixtures)} Python phase lifecycle fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
