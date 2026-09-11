#!/usr/bin/env python3
"""Freeze switch-to-replacement-initiative policy from the pinned Python runtime."""
from __future__ import annotations

import argparse
import ast
import inspect
import sys
import textwrap
from pathlib import Path


def _expr(node: ast.AST | None) -> str:
    if node is None:
        return "<missing>"
    return ast.unparse(node)


def _apply_switch_contract(method) -> tuple[str, str, str, str]:
    source = textwrap.dedent(inspect.getsource(method))
    tree = ast.parse(source)
    function = tree.body[0]
    assert isinstance(function, (ast.FunctionDef, ast.AsyncFunctionDef))

    defaults = {
        argument.arg: ("<required>" if default is None else _expr(default))
        for argument, default in zip(
            function.args.kwonlyargs,
            function.args.kw_defaults,
            strict=True,
        )
    }

    guarded_call = None
    for node in ast.walk(function):
        if not isinstance(node, ast.If) or _expr(node.test) != "allow_replacement_turn":
            continue
        for child in ast.walk(node):
            if not isinstance(child, ast.Call):
                continue
            if _expr(child.func) != "self._insert_replacement_initiative":
                continue
            guarded_call = child
            break
        if guarded_call is not None:
            break

    if guarded_call is None:
        raise AssertionError("Pinned _apply_switch no longer guards replacement initiative insertion")

    immediate_keyword = next(
        (keyword for keyword in guarded_call.keywords if keyword.arg == "allow_immediate"),
        None,
    )
    if immediate_keyword is None:
        raise AssertionError("Pinned _apply_switch no longer forwards allow_immediate")

    replacement_arg = _expr(guarded_call.args[0]) if guarded_call.args else "<missing>"
    forwarded_immediate = _expr(immediate_keyword.value)
    return (
        defaults.get("allow_replacement_turn", "<missing>"),
        defaults.get("allow_immediate", "<missing>"),
        replacement_arg,
        forwarded_immediate,
    )


class _ApplySwitchCallerVisitor(ast.NodeVisitor):
    def __init__(self) -> None:
        self.scope: list[str] = []
        self.rows: list[tuple[str, int, str, str, str, str]] = []
        self._caller_counts: dict[str, int] = {}

    def visit_ClassDef(self, node: ast.ClassDef) -> None:
        self.scope.append(node.name)
        self.generic_visit(node)
        self.scope.pop()

    def visit_FunctionDef(self, node: ast.FunctionDef) -> None:
        self.scope.append(node.name)
        self.generic_visit(node)
        self.scope.pop()

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> None:
        self.scope.append(node.name)
        self.generic_visit(node)
        self.scope.pop()

    def visit_Call(self, node: ast.Call) -> None:
        if isinstance(node.func, ast.Attribute) and node.func.attr == "_apply_switch":
            caller = ".".join(self.scope) or "<module>"
            index = self._caller_counts.get(caller, 0)
            self._caller_counts[caller] = index + 1
            keywords = {keyword.arg: keyword.value for keyword in node.keywords if keyword.arg is not None}
            replacement = keywords.get("replacement_id")
            if replacement is None and len(node.args) > 1:
                replacement = node.args[1]
            self.rows.append(
                (
                    caller,
                    index,
                    _expr(node.func.value),
                    _expr(keywords.get("allow_replacement_turn")),
                    _expr(keywords.get("allow_immediate")),
                    _expr(replacement),
                )
            )
        self.generic_visit(node)


def _discover_callers(module) -> list[tuple[str, int, str, str, str, str]]:
    source = inspect.getsource(module)
    tree = ast.parse(source)
    visitor = _ApplySwitchCallerVisitor()
    visitor.visit(tree)
    return visitor.rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    import auto_ptu.rules.battle_state as battle_state_module
    from auto_ptu.rules.battle_state import BattleState

    turn_policy, immediate_policy, replacement_arg, forwarded_immediate = _apply_switch_contract(
        BattleState._apply_switch
    )
    if (turn_policy, immediate_policy, replacement_arg, forwarded_immediate) != (
        "<required>",
        "<required>",
        "replacement_id",
        "allow_immediate",
    ):
        raise AssertionError(
            "Pinned _apply_switch replacement initiative handoff changed: "
            f"{turn_policy=}, {immediate_policy=}, {replacement_arg=}, {forwarded_immediate=}"
        )

    caller_rows = _discover_callers(battle_state_module)
    if not caller_rows:
        raise AssertionError("Pinned battle_state module no longer contains any _apply_switch callers")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        handle.write(
            "APPLY_SWITCH\t"
            f"{turn_policy}\t{immediate_policy}\t{replacement_arg}\t{forwarded_immediate}\n"
        )
        for caller, index, receiver, allow_turn, allow_immediate, replacement in caller_rows:
            handle.write(
                "CALLER\t"
                f"{caller}\t{index}\t{receiver}\t{allow_turn}\t{allow_immediate}\t{replacement}\n"
            )
    print(output)


if __name__ == "__main__":
    main()
