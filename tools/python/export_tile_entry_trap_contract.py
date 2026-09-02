#!/usr/bin/env python3
"""Freeze the pinned Python tile-entry terrain-trap contract."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path

SOURCE = Path("auto_ptu/rules/battle_state.py")
FUNCTION = "_trigger_tile_traps_on_entry"


def compact(node: ast.AST) -> str:
    return " ".join(ast.unparse(node).split())


def find_function(tree: ast.AST) -> ast.FunctionDef:
    matches = [node for node in ast.walk(tree) if isinstance(node, ast.FunctionDef) and node.name == FUNCTION]
    if len(matches) != 1:
        raise SystemExit(f"expected exactly one {FUNCTION}, found {len(matches)}")
    return matches[0]


def require(fn: ast.AST, fragment: str) -> None:
    text = compact(fn)
    if fragment not in text:
        raise SystemExit(f"{FUNCTION} contract drifted: missing {fragment!r}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_root / SOURCE
    tree = ast.parse(source.read_text(encoding="utf-8-sig"), filename=str(source))
    fn = find_function(tree)

    # Freeze the rule-family seams before Java owns tile metadata.
    for fragment in (
        "tile_meta.get('traps')",
        "tile_meta.get('trap_sources')",
        "int(layers or 0) <= 0",
        "self._team_for(source_id) == self._team_for(actor_id)",
        "actor.naturewalk_labels()",
        "'type': 'trap'",
        "'effect': 'trigger'",
        "'source_id': source_id or None",
        "'terrains': sorted(terrains)",
        "'coord': list(actor.position)",
        "'target_hp': actor.hp",
        "self._consume_trap(actor.position, trap_key)",
    ):
        require(fn, fragment)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "contract\tvalue\n"
        "zero_layers\tskip\n"
        "same_team_source\tskip\n"
        "naturewalk_match\thas_special_branch\n"
        "enemy_entry\ttrigger\n"
        "trigger_consumption\twhole_trap_key\n"
        "trigger_provenance\tsource_id,terrains,coord,target_hp\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
