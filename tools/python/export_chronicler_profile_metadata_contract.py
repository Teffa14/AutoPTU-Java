#!/usr/bin/env python3
"""Freeze pinned-oracle Chronicler metadata materialization semantics."""
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
        "records_have_three_canonical_kinds": all(f"'{kind}': []" in text for kind in ("profile", "technique", "travel")),
        "missing_trainer_uses_empty_trainer_class": "if trainer is not None else {}" in text,
        "non_mapping_trainer_class_returns_empty_metadata": "if not isinstance(trainer_class, dict)" in text and "'travel_ability': ''" in text,
        "metadata_comes_from_trainer_class": "trainer_class.get('chronicler_archives'" in text and "trainer_class.get('chronicler_records'" in text,
        "archives_use_canonical_alias_normalizer": "_normalize_chronicler_archive_kind(entry)" in text,
        "records_require_mapping": "raw_records = trainer_class.get('chronicler_records', {})" in text and "isinstance(raw_records, dict)" in text,
        "records_normalize_names": "_normalize_chronicler_record_name(entry)" in text,
        "all_record_kinds_dedupe_case_insensitive": "for kind in ('profile', 'technique', 'travel')" in text and "key = label.lower()" in text and "key in seen" in text and "seen.add(key)" in text,
        "travel_ability_uses_canonical_alias_map": "_CHRONICLER_TRAVEL_ABILITY_ALIASES.get" in text and "trainer_class.get('chronicler_travel_ability')" in text,
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
