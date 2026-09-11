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
        argument.arg: _expr(default)
        for argument, default in zip(
            function.args.kwonlyargs,
            function.args.kw_defaults,
            strict=True,
        )
        if default is not None
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


def _caller_contract(method) -> list[tuple[str, str, str]]:
    source = textwrap.dedent(inspect.getsource(method))
    tree = ast.parse(source)
    function = tree.body[0]
    assert isinstance(function, (ast.FunctionDef, ast.AsyncFunctionDef))

    result: list[tuple[str, str, str]] = []
    for node in ast.walk(function):
        if not isinstance(node, ast.Call) or _expr(node.func) != "self._apply_switch":
            continue
        keywords = {keyword.arg: keyword.value for keyword in node.keywords if keyword.arg is not None}
        result.append(
            (
                _expr(keywords.get("allow_replacement_turn")),
                _expr(keywords.get("allow_immediate")),
                _expr(node.args[1]) if len(node.args) > 1 else _expr(keywords.get("replacement")),
            )
        )
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.rules.battle_state import BattleState

    default_turn, default_immediate, replacement_arg, forwarded_immediate = _apply_switch_contract(
        BattleState._apply_switch
    )
    if (default_turn, default_immediate, replacement_arg, forwarded_immediate) != (
        "True",
        "False",
        "replacement",
        "allow_immediate",
    ):
        raise AssertionError(
            "Pinned _apply_switch replacement initiative handoff changed: "
            f"{default_turn=}, {default_immediate=}, {replacement_arg=}, {forwarded_immediate=}"
        )

    caller_names = (
        "_perform_forced_switch",
        "_ensure_replacement_or_lose",
        "_replace_fainted_ally",
    )
    caller_rows: list[tuple[str, int, str, str, str]] = []
    for caller_name in caller_names:
        method = getattr(BattleState, caller_name)
        calls = _caller_contract(method)
        if not calls:
            raise AssertionError(f"Pinned {caller_name} no longer calls _apply_switch")
        for index, (allow_turn, allow_immediate, replacement) in enumerate(calls):
            caller_rows.append((caller_name, index, allow_turn, allow_immediate, replacement))

    expected_fragments = {
        "_perform_forced_switch": ("self._switch_allows_entry_turn(target_name)", "False"),
        "_ensure_replacement_or_lose": ("self._switch_allows_entry_turn(replacement)", "True"),
        "_replace_fainted_ally": ("True", "True"),
    }
    for caller_name, expected in expected_fragments.items():
        matches = [row for row in caller_rows if row[0] == caller_name]
        if not any((row[2], row[3]) == expected for row in matches):
            raise AssertionError(
                f"Pinned {caller_name} policy changed; expected allow flags {expected}, got {matches}"
            )

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        handle.write(
            "APPLY_SWITCH\t"
            f"{default_turn}\t{default_immediate}\t{replacement_arg}\t{forwarded_immediate}\n"
        )
        for caller_name, index, allow_turn, allow_immediate, replacement in caller_rows:
            handle.write(
                "CALLER\t"
                f"{caller_name}\t{index}\t{allow_turn}\t{allow_immediate}\t{replacement}\n"
            )
    print(output)


if __name__ == "__main__":
    main()
