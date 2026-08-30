#!/usr/bin/env python3
import argparse
import ast
from pathlib import Path


def find_attempt_intercept(tree: ast.AST) -> ast.FunctionDef:
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == "_attempt_intercept":
            return node
    raise RuntimeError("_attempt_intercept not found in pinned Python oracle")


def assignment_line(fn: ast.AST, name: str) -> int:
    lines = []
    for node in ast.walk(fn):
        if isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name) and target.id == name:
                    lines.append(node.lineno)
        elif isinstance(node, ast.AnnAssign):
            if isinstance(node.target, ast.Name) and node.target.id == name:
                lines.append(node.lineno)
    return min(lines, default=-1)


def line_tile_skip_line(fn: ast.AST, line_tiles_line: int, check_line: int) -> int:
    """Return the oracle continue that guards an unusable line_tiles result.

    This intentionally follows Python AST shape rather than source formatting. The pinned oracle may
    spell the guard as a positive condition with an else branch instead of `if not line_tiles`.
    """
    candidates = []
    for node in ast.walk(fn):
        if not isinstance(node, ast.If):
            continue
        if not any(isinstance(child, ast.Name) and child.id == "line_tiles" for child in ast.walk(node.test)):
            continue
        for child in ast.walk(node):
            if isinstance(child, ast.Continue) and child.lineno > line_tiles_line:
                if check_line < 0 or child.lineno < check_line:
                    candidates.append(child.lineno)
    return min(candidates, default=-1)


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
    line_tiles_line = assignment_line(fn, "line_tiles")
    check_line = assignment_line(fn, "success")
    no_line_continue_line = line_tile_skip_line(fn, line_tiles_line, check_line)

    contract = {
        "candidate_sort_uses_footprint_distance": int("interceptors.sort(key=lambda item: targeting.footprint_distance(" in source),
        "candidate_sort_targets_medium_anchor": int("target_pos, 'Medium', self.grid" in source),
        "attack_line_uses_line_cells": int("line = self._line_cells(attacker.position, target_pos)" in source),
        "off_line_uses_legal_shift_tiles": int("reachable = movement.legal_shift_tiles(self, interceptor_id)" in source and "line_tiles = [coord for coord in line if coord in reachable]" in source),
        "no_legal_line_tile_skips_candidate": int(line_tiles_line >= 0 and no_line_continue_line > line_tiles_line),
        "no_legal_line_tile_skips_before_check": int(no_line_continue_line >= 0 and check_line > no_line_continue_line),
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

    missing = [key for key, value in contract.items() if value != 1]
    if missing:
        raise RuntimeError("intercept geometry contract changed: " + ", ".join(missing))


if __name__ == "__main__":
    main()
