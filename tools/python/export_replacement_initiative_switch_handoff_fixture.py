#!/usr/bin/env python3
"""Freeze switch-to-replacement-initiative policy from the pinned Python runtime."""
from __future__ import annotations

import argparse
import ast
import inspect
import sys
import textwrap
from pathlib import Path


CallerRow = tuple[str, int, str, str, str, str]
EXPECTED_CALLERS: list[CallerRow] = [
    ("PokemonState.SwitchAction.resolve", 0, "battle", "False", "False", "self.replacement_id"),
    ("PokemonState.TrainerSwitchAction.resolve", 0, "battle", "allow_turn", "not battle.is_league_battle()", "self.replacement_id"),
    ("PokemonState.QuickSwitchAction.resolve", 0, "battle", "True", "False", "self.replacement_id"),
    ("PokemonState.BattleState._maybe_trigger_round_trip", 0, "self", "True", "True", "choice_id"),
    ("PokemonState.BattleState._maybe_trigger_quick_switch", 0, "self", "True", "False", "choice_id"),
    ("PokemonState.BattleState._execute_parting_shot", 0, "self", "True", "True", "replacement_id"),
    ("PokemonState.BattleState.resolve_move_targets", 0, "self", "allow_replacement_turn", "True", "replacement_id"),
]


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
        for argument, default in zip(function.args.kwonlyargs, function.args.kw_defaults, strict=True)
    }

    guarded_call = None
    for node in ast.walk(function):
        if not isinstance(node, ast.If) or _expr(node.test) != "allow_replacement_turn":
            continue
        for child in ast.walk(node):
            if isinstance(child, ast.Call) and _expr(child.func) == "self._insert_replacement_initiative":
                guarded_call = child
                break
        if guarded_call is not None:
            break

    if guarded_call is None:
        raise AssertionError("Pinned _apply_switch no longer guards replacement initiative insertion")

    immediate_keyword = next((keyword for keyword in guarded_call.keywords if keyword.arg == "allow_immediate"), None)
    if immediate_keyword is None:
        raise AssertionError("Pinned _apply_switch no longer forwards allow_immediate")

    replacement_arg = _expr(guarded_call.args[0]) if guarded_call.args else "<missing>"
    return (
        defaults.get("allow_replacement_turn", "<missing>"),
        defaults.get("allow_immediate", "<missing>"),
        replacement_arg,
        _expr(immediate_keyword.value),
    )


class _ApplySwitchCallerVisitor(ast.NodeVisitor):
    def __init__(self) -> None:
        self.scope: list[str] = []
        self.rows: list[CallerRow] = []
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
            self.rows.append((
                caller,
                index,
                _expr(node.func.value),
                _expr(keywords.get("allow_replacement_turn")),
                _expr(keywords.get("allow_immediate")),
                _expr(replacement),
            ))
        self.generic_visit(node)


def _discover_callers(module) -> list[CallerRow]:
    visitor = _ApplySwitchCallerVisitor()
    visitor.visit(ast.parse(inspect.getsource(module)))
    return visitor.rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    import auto_ptu.rules.battle_state as battle_state_module
    from auto_ptu.rules.battle_state import BattleState

    contract = _apply_switch_contract(BattleState._apply_switch)
    expected_contract = ("<required>", "<required>", "replacement_id", "allow_immediate")
    if contract != expected_contract:
        raise AssertionError(f"Pinned _apply_switch replacement initiative handoff changed: expected={expected_contract!r}, actual={contract!r}")

    caller_rows = _discover_callers(battle_state_module)
    if caller_rows != EXPECTED_CALLERS:
        expected = "\n".join(f"  {row!r}" for row in EXPECTED_CALLERS)
        actual = "\n".join(f"  {row!r}" for row in caller_rows)
        raise AssertionError(
            "Pinned _apply_switch caller policy set changed. Review policy before changing Java runtime.\n"
            f"Expected:\n{expected}\nActual:\n{actual}"
        )

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        handle.write("APPLY_SWITCH\t" + "\t".join(contract) + "\n")
        for caller, index, receiver, allow_turn, allow_immediate, replacement in caller_rows:
            handle.write("CALLER\t" f"{caller}\t{index}\t{receiver}\t{allow_turn}\t{allow_immediate}\t{replacement}\n")
    print(output)


if __name__ == "__main__":
    main()
