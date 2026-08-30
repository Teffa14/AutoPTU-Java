#!/usr/bin/env python3
import argparse
import ast
from pathlib import Path


def find_attempt_intercept(tree: ast.AST) -> ast.FunctionDef:
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == "_attempt_intercept":
            return node
    raise RuntimeError("_attempt_intercept not found in pinned Python oracle")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    path = Path(args.source_root) / "auto_ptu" / "rules" / "battle_state.py"
    tree = ast.parse(path.read_text(encoding="utf-8"))
    fn = find_attempt_intercept(tree)
    source = ast.unparse(fn)
    line_tile_index = source.find("target_pos = line_tiles[0]")
    line_tile_distance_index = source.find("distance = targeting.footprint_distance(", line_tile_index)

    contract = {
        "candidate_sort_uses_footprint_distance": int("interceptors.sort(key=lambda item: targeting.footprint_distance(" in source),
        "candidate_sort_targets_medium_anchor": int("target_pos, 'Medium', self.grid" in source),
        "attack_line_uses_line_cells": int("line = self._line_cells(attacker.position, target_pos)" in source),
        "off_line_uses_legal_shift_tiles": int("reachable = movement.legal_shift_tiles(self, interceptor_id)" in source and "line_tiles = [coord for coord in line if coord in reachable]" in source),
        "line_tile_sort_uses_footprint_distance": int("line_tiles.sort(key=lambda coord: targeting.footprint_distance(" in source),
        "line_tile_sort_targets_medium_anchor": int("coord, 'Medium', self.grid" in source),
        "check_distance_uses_footprint_distance": int("distance = targeting.footprint_distance(" in source),
        "check_distance_targets_medium_anchor": int("target_pos,\n                'Medium',\n                self.grid" in source or "target_pos, 'Medium', self.grid" in source),
        "check_distance_floor_one": int("if distance <= 0:" in source and "distance = 1" in source),
        "line_tile_recomputes_check_distance": int(line_tile_index >= 0 and line_tile_distance_index > line_tile_index),
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("key\tvalue\n" + "".join(f"{key}\t{value}\n" for key, value in contract.items()), encoding="utf-8")


if __name__ == "__main__":
    main()
