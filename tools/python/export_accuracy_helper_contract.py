#!/usr/bin/env python3
"""Freeze pinned-oracle Focused Training/Chronicler Accuracy helper behavior."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path

HELPERS = (
    "_focused_training_accuracy_bonus",
    "_chronicler_accuracy_bonus",
)


def defined_helpers(source_root: Path) -> set[str]:
    found: set[str] = set()
    authoritative_package = source_root / "auto_ptu"
    for path in authoritative_package.rglob("*.py"):
        try:
            tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
        except (UnicodeDecodeError, SyntaxError):
            continue
        for node in ast.walk(tree):
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name in HELPERS:
                found.add(node.name)
    return found


def find_helper(source_root: Path, helper_name: str) -> ast.FunctionDef:
    authoritative_package = source_root / "auto_ptu"
    matches: list[ast.FunctionDef] = []
    for path in authoritative_package.rglob("*.py"):
        try:
            tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
        except (UnicodeDecodeError, SyntaxError):
            continue
        for node in ast.walk(tree):
            if isinstance(node, ast.FunctionDef) and node.name == helper_name:
                matches.append(node)
    if len(matches) != 1:
        raise RuntimeError(f"Expected one {helper_name} definition, found {len(matches)}")
    return matches[0]


def temporary_accuracy_contract(source_root: Path) -> tuple[bool, bool]:
    path = source_root / "auto_ptu" / "rules" / "calculations.py"
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    function = next(
        node for node in tree.body
        if isinstance(node, ast.FunctionDef) and node.name == "_temporary_accuracy_bonus"
    )
    text = ast.unparse(function)
    focused_fallback = "bonus += 1" in text and "_focused_training_accuracy_bonus" in text
    chronicler_optional = "hasattr(battle, '_chronicler_accuracy_bonus')" in text
    return focused_fallback, chronicler_optional


def focused_behavior(source_root: Path) -> dict[str, bool]:
    function = find_helper(source_root, "_focused_training_accuracy_bonus")
    text = ast.unparse(function)
    final_return = function.body[-1] if function.body else None
    return {
        "focused_requires_training_effect": "get_temporary_effects('focused_training')" in text,
        "focused_checks_duelist_feature": "has_trainer_feature('Duelist')" in text,
        "focused_checks_any_controller_tag": "_any_duelist_tag_for_controller(attacker.controller_id)" in text,
        "focused_requires_tagged_defender_for_duelist": "_is_duelist_tagged_for(defender, attacker)" in text,
        "focused_uses_ceil_half_momentum": "math.ceil(self._duelist_momentum(attacker) / 2.0)" in text,
        "focused_default_bonus_is_one": (
            isinstance(final_return, ast.Return)
            and isinstance(final_return.value, ast.Constant)
            and final_return.value.value == 1
        ),
    }


def chronicler_behavior(source_root: Path) -> dict[str, bool]:
    function = find_helper(source_root, "_chronicler_accuracy_bonus")
    text = ast.unparse(function)
    return {
        "chronicler_iterates_targeted_profiling": "get_temporary_effects('targeted_profiling')" in text,
        "chronicler_expiry_is_strictly_after_round": "self.round > int(expires_round)" in text,
        "chronicler_removes_expired_entries": "attacker.temporary_effects.remove(entry)" in text,
        "chronicler_source_controller_falls_back_to_attacker": (
            "entry.get('source_controller') or attacker.controller_id" in text
        ),
        "chronicler_requires_profile_match": "_chronicler_profile_matches(source_controller, defender)" in text,
        "chronicler_adds_two_per_match": "bonus += 2" in text,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    found = defined_helpers(args.source_root)
    focused_fallback, chronicler_optional = temporary_accuracy_contract(args.source_root)
    focused = focused_behavior(args.source_root)
    chronicler = chronicler_behavior(args.source_root)

    rows = [
        ("focused_helper_defined", int("_focused_training_accuracy_bonus" in found)),
        ("chronicler_helper_defined", int("_chronicler_accuracy_bonus" in found)),
        ("focused_fallback_is_one", int(focused_fallback)),
        ("chronicler_is_optional", int(chronicler_optional)),
        *((name, int(value)) for name, value in focused.items()),
        *((name, int(value)) for name, value in chronicler.items()),
    ]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "property\texpected\n" + "".join(f"{name}\t{value}\n" for name, value in rows),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
