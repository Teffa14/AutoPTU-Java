#!/usr/bin/env python3
"""Export observable structure of the pinned Python apply_forced_movement implementation."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path

SOURCE = Path("auto_ptu/rules/battle_state.py")
FUNCTION = "apply_forced_movement"


def compact(node: ast.AST) -> str:
    return " ".join(ast.unparse(node).split()).replace("\t", " ")


def call_symbol(call: ast.Call) -> str:
    func = call.func
    if isinstance(func, ast.Name):
        return func.id
    if isinstance(func, ast.Attribute):
        parts: list[str] = [func.attr]
        owner = func.value
        while isinstance(owner, ast.Attribute):
            parts.append(owner.attr)
            owner = owner.value
        if isinstance(owner, ast.Name):
            parts.append(owner.id)
        return ".".join(reversed(parts))
    return compact(func)


def target_text(node: ast.AST) -> str:
    if isinstance(node, ast.Assign):
        return ",".join(compact(target) for target in node.targets)
    if isinstance(node, ast.AnnAssign):
        return compact(node.target)
    if isinstance(node, ast.AugAssign):
        return compact(node.target)
    return ""


def find_function(tree: ast.AST) -> ast.FunctionDef | ast.AsyncFunctionDef:
    matches = [
        node for node in ast.walk(tree)
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == FUNCTION
    ]
    if len(matches) != 1:
        raise SystemExit(f"expected exactly one {FUNCTION}, found {len(matches)}")
    return matches[0]


def observable_rows(fn: ast.AST) -> list[tuple[int, str, str, str]]:
    rows: list[tuple[int, str, str, str]] = []
    for node in ast.walk(fn):
        line = getattr(node, "lineno", -1)
        if isinstance(node, ast.Return):
            rows.append((line, "return", "", compact(node)))
        elif isinstance(node, ast.Call):
            rows.append((line, "call", call_symbol(node), compact(node)))
        elif isinstance(node, (ast.Assign, ast.AnnAssign, ast.AugAssign)):
            target = target_text(node)
            kind = "position_write" if "position" in target.lower() else "write"
            rows.append((line, kind, target, compact(node)))
    return sorted(rows, key=lambda row: (row[0], row[1], row[2], row[3]))


def require_insectoid_feature_event_contract(rows: list[tuple[int, str, str, str]]) -> None:
    """Freeze the Python semantic-event obligation for Insectoid Utility push prevention.

    Java can preserve prevention provenance internally without yet exposing the Python event. This
    guard makes that gap explicit: if the pinned oracle stops mentioning the Feature, Wallclimber,
    or trainer_feature inside apply_forced_movement, the parity fixture must be reviewed instead of
    silently accepting a changed observable contract.
    """
    statements = "\n".join(statement for _, _, _, statement in rows)
    required_fragments = ("Insectoid Utility", "Wallclimber", "trainer_feature")
    missing = [fragment for fragment in required_fragments if fragment not in statements]
    if missing:
        raise SystemExit(
            "apply_forced_movement lost the pinned Insectoid Utility semantic-event contract: "
            + ", ".join(missing)
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source = args.source_root / SOURCE
    tree = ast.parse(source.read_text(encoding="utf-8-sig"), filename=str(source))
    fn = find_function(tree)
    rows = observable_rows(fn)

    if not rows:
        raise SystemExit("apply_forced_movement observable inventory is empty")
    if not any(kind == "return" for _, kind, _, _ in rows):
        raise SystemExit("apply_forced_movement no longer exposes an explicit return")
    if not any(kind == "call" for _, kind, _, _ in rows):
        raise SystemExit("apply_forced_movement no longer contains observable calls")
    if not any(kind in {"write", "position_write"} for _, kind, _, _ in rows):
        raise SystemExit("apply_forced_movement no longer contains state writes")
    require_insectoid_feature_event_contract(rows)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["path\tfunction\tline\tkind\tsymbol\tstatement"]
    for line, kind, symbol, statement in rows:
        lines.append("\t".join((SOURCE.as_posix(), FUNCTION, str(line), kind, symbol, statement)))
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
