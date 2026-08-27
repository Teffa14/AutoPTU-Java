#!/usr/bin/env python3
"""Freeze held-item START slot-order semantics from the pinned Python oracle."""

from __future__ import annotations

import argparse
import ast
from pathlib import Path


def _method_source(path: Path, class_name: str, method_name: str) -> str:
    source = path.read_text(encoding="utf-8")
    tree = ast.parse(source)
    for node in tree.body:
        if isinstance(node, ast.ClassDef) and node.name == class_name:
            for child in node.body:
                if isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef)) and child.name == method_name:
                    return ast.get_source_segment(source, child) or ""
    raise RuntimeError(f"{class_name}.{method_name} not found in {path}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    path = Path(args.source_root) / "auto_ptu/rules/controllers/item_system.py"
    start = _method_source(path, "ItemSystem", "apply_held_item_start")

    sorted_marker = "sorted(battle._iter_held_items(actor), key=lambda row: row[0], reverse=True)"
    loop_marker = "for idx, item, entry in held_items:"
    properties = {
        "start_sorts_held_items": sorted_marker in start,
        "start_orders_by_slot_index": "key=lambda row: row[0]" in start,
        "start_orders_slots_descending": "reverse=True" in start,
        "start_preserves_slot_identity_in_loop": loop_marker in start,
        "sorting_precedes_item_processing": start.find(sorted_marker) >= 0
        and start.find(loop_marker) > start.find(sorted_marker),
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "".join(f"{key}\t{1 if value else 0}\n" for key, value in properties.items()),
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
