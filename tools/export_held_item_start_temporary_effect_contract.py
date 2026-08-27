#!/usr/bin/env python3
"""Freeze generic held-item START temporary-effect semantics from the pinned Python oracle."""

from __future__ import annotations

import ast
import sys
from pathlib import Path


def _method_source(path: Path) -> str:
    source = path.read_text(encoding="utf-8")
    tree = ast.parse(source)
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == "apply_held_item_start":
            return ast.get_source_segment(source, node) or ""
    raise RuntimeError("apply_held_item_start not found")


def main() -> int:
    if len(sys.argv) != 3:
        raise SystemExit("usage: export_held_item_start_temporary_effect_contract.py <python-repo> <output-tsv>")
    repo = Path(sys.argv[1])
    out = Path(sys.argv[2])
    method = _method_source(repo / "auto_ptu/rules/controllers/item_system.py")

    properties = {
        "base_stat_changes_before_scalars": method.find('effects.get("base_stat_changes")') < method.find('effects.get("base_stat_scalars")'),
        "scalars_before_accuracy": method.find('effects.get("base_stat_scalars")') < method.find('effects.get("accuracy_bonus")'),
        "accuracy_before_evasion": method.find('effects.get("accuracy_bonus")') < method.find('effects.get("evasion_bonus_spd")'),
        "stat_modifier_duplicate_key_stat_source": '_has_temp_effect("stat_modifier", stat=stat, source=name)' in method,
        "stat_scalar_duplicate_key_stat_source": '_has_temp_effect("stat_scalar", stat=stat, source=name)' in method,
        "accuracy_carries_null_type": '_has_temp_effect("accuracy_bonus", amount=int(accuracy_bonus), type=None, source=name)' in method
            and 'actor.add_temporary_effect("accuracy_bonus", amount=int(accuracy_bonus), type=None, source=name)' in method,
        "status_evasion_scope": '_has_temp_effect(\n                    "evasion_bonus", scope="status", amount=int(evasion_spd), source=name' in method,
        "all_evasion_scope": '_has_temp_effect(\n                    "evasion_bonus", scope="all", amount=int(evasion_all), source=name' in method,
        "source_is_display_name": 'name = _item_name_text(item)' in method,
    }
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("".join(f"{key}\t{1 if value else 0}\n" for key, value in properties.items()), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
