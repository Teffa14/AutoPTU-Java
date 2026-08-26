#!/usr/bin/env python3
"""Freeze pinned-oracle ownership of Focused Training/Chronicler Accuracy helpers."""
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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    found = defined_helpers(args.source_root)
    focused_fallback, chronicler_optional = temporary_accuracy_contract(args.source_root)

    rows = [
        ("focused_helper_defined", int("_focused_training_accuracy_bonus" in found)),
        ("chronicler_helper_defined", int("_chronicler_accuracy_bonus" in found)),
        ("focused_fallback_is_one", int(focused_fallback)),
        ("chronicler_is_optional", int(chronicler_optional)),
    ]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "property\texpected\n" + "".join(f"{name}\t{value}\n" for name, value in rows),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
