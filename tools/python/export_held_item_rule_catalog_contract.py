#!/usr/bin/env python3
"""Freeze held-item catalog lookup and START parsing ownership from the pinned Python oracle."""

from __future__ import annotations

import argparse
import ast
from pathlib import Path


def _function_source(path: Path, name: str) -> str:
    source = path.read_text(encoding="utf-8")
    tree = ast.parse(source)
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == name:
            return ast.get_source_segment(source, node) or ""
    raise RuntimeError(f"{name} not found in {path}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    root = Path(args.source_root)

    lookup = _function_source(root / "auto_ptu/rules/item_catalog.py", "get_item_entry")
    compact = _function_source(root / "auto_ptu/rules/item_catalog.py", "_compact_key")
    start = _function_source(root / "auto_ptu/rules/controllers/item_system.py", "apply_held_item_start")

    properties = {
        "lookup_normalizes_strip_lower": 'name.strip().lower()' in lookup,
        "exact_lookup_precedes_compact_fallback": lookup.find('catalog.get(key)') < lookup.find('_compact_key(key)'),
        "compact_removes_non_alphanumeric": 're.sub(r"[^a-z0-9]+", ""' in compact,
        "compact_fallback_scans_catalog": 'for candidate, entry in catalog.items()' in lookup,
        "start_uses_catalog_entry_parser": 'effects = parse_item_effects(entry)' in start,
        "start_uses_display_item_name_as_source": 'name = _item_name_text(item)' in start,
        "start_uses_entry_normalized_name_for_rules": 'normalized = entry.normalized_name()' in start,
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("".join(f"{key}\t{1 if value else 0}\n" for key, value in properties.items()), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
