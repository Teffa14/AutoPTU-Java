#!/usr/bin/env python3
"""Freeze pinned-oracle Chronicler profile metadata materialization semantics."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def find_helper(source_root: Path, helper_name: str) -> ast.FunctionDef:
    root = source_root / "auto_ptu"
    matches: list[ast.FunctionDef] = []
    for path in root.rglob("*.py"):
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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    function = find_helper(args.source_root, "_chronicler_metadata")
    text = ast.unparse(function)
    properties = {
        "archives_start_empty_set": "archives: Set[str] = set()" in text or "archives = set()" in text,
        "records_have_four_canonical_kinds": all(f"'{kind}': []" in text for kind in ("pokemon", "move", "ability", "trainer")),
        "missing_trainer_returns_empty_metadata": "if trainer is None" in text,
        "metadata_comes_from_chronicler_feature": "_trainer_feature_definition(trainer, 'Chronicler')" in text,
        "requires_trainer_class_mapping": "trainer_class = chronicler_feature.get('trainer_class')" in text,
        "archives_are_trimmed_lowercase": "str(entry).strip().lower()" in text and "archives.add(token)" in text,
        "records_require_mapping": "raw_records = trainer_class.get('chronicler_records', {})" in text and "isinstance(raw_records, dict)" in text,
        "record_values_require_lists": "if not isinstance(values, list)" in text,
        "pokemon_and_ability_dedupe_case_insensitive": "if kind in {'pokemon', 'ability'}" in text and "key = label.lower()" in text,
        "move_and_trainer_preserve_entries": "records[kind].append(label)" in text,
    }

    failed = [name for name, value in properties.items() if not value]
    if failed:
        raise RuntimeError("Pinned Chronicler metadata contract changed: " + ", ".join(failed))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "property\texpected\n" + "".join(f"{name}\t1\n" for name in properties),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
