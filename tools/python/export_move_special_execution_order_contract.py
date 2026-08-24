#!/usr/bin/env python3
"""Discover and freeze where Python executes move-special phases.

This extractor intentionally derives the contract from the pinned AutoPTU source tree.
It reports every handle_move_specials() call together with its enclosing function and
nearby ordered battle-pipeline calls. The normalized manifest is hashed so a source
reordering cannot silently move a Java hook to a different semantic phase.
"""
from __future__ import annotations

import ast
import hashlib
import json
import os
from pathlib import Path

EXPECTED_DIGEST = "743ef231a164727cee549d39d4c2b7a898c64cd7c4365931b71008267bdeff53"
INTERESTING_TOKENS = (
    "move_special",
    "pre_damage",
    "interrupt",
    "shield",
    "post_result",
    "post_damage",
    "item",
    "damage",
    "hp",
    "end_action",
    "record_move",
    "mark_action",
)


def call_name(node: ast.Call) -> str:
    fn = node.func
    parts: list[str] = []
    while isinstance(fn, ast.Attribute):
        parts.append(fn.attr)
        fn = fn.value
    if isinstance(fn, ast.Name):
        parts.append(fn.id)
    return ".".join(reversed(parts))


def literal_phase(call: ast.Call) -> str:
    for kw in call.keywords:
        if kw.arg == "phase" and isinstance(kw.value, ast.Constant) and isinstance(kw.value.value, str):
            return kw.value.value.strip().lower()
    return "post_damage"


def source_segment(source: str, node: ast.AST) -> str:
    value = ast.get_source_segment(source, node) or ""
    return " ".join(value.strip().split())


def enclosing_function(tree: ast.AST, target: ast.AST) -> ast.FunctionDef | ast.AsyncFunctionDef | None:
    parents: dict[ast.AST, ast.AST] = {}
    for parent in ast.walk(tree):
        for child in ast.iter_child_nodes(parent):
            parents[child] = parent
    cur: ast.AST | None = target
    while cur is not None:
        if isinstance(cur, (ast.FunctionDef, ast.AsyncFunctionDef)):
            return cur
        cur = parents.get(cur)
    return None


def ordered_markers(source: str, fn: ast.FunctionDef | ast.AsyncFunctionDef) -> list[dict[str, object]]:
    markers: list[dict[str, object]] = []
    for node in ast.walk(fn):
        if not isinstance(node, ast.Call):
            continue
        name = call_name(node)
        lowered = name.lower()
        if "handle_move_specials" in lowered:
            label = f"move_special:{literal_phase(node)}"
        elif any(token in lowered for token in INTERESTING_TOKENS):
            label = name
        else:
            continue
        markers.append({"line": getattr(node, "lineno", -1), "label": label, "source": source_segment(source, node)[:240]})
    markers.sort(key=lambda item: (int(item["line"]), str(item["label"])))
    return markers


def main() -> None:
    root = Path(os.environ.get("AUTOPTU_PYTHON_ROOT", "../AutoPTU")).resolve()
    package = root / "auto_ptu"
    if not package.is_dir():
        raise SystemExit(f"AutoPTU package not found: {package}")

    records: list[dict[str, object]] = []
    phases: set[str] = set()
    for path in sorted(package.rglob("*.py")):
        source = path.read_text(encoding="utf-8")
        try:
            tree = ast.parse(source, filename=str(path))
        except SyntaxError:
            continue
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call) or "handle_move_specials" not in call_name(node):
                continue
            fn = enclosing_function(tree, node)
            if fn is None:
                continue
            phase = literal_phase(node)
            phases.add(phase)
            records.append({
                "file": str(path.relative_to(root)).replace("\\", "/"),
                "function": fn.name,
                "line": node.lineno,
                "phase": phase,
                "call": source_segment(source, node)[:320],
                "ordered_markers": ordered_markers(source, fn),
            })

    records.sort(key=lambda r: (str(r["file"]), str(r["function"]), int(r["line"]), str(r["phase"])))
    manifest = {"phases": sorted(phases), "callsites": records}
    normalized = json.dumps(manifest, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
    digest = hashlib.sha256(normalized.encode("utf-8")).hexdigest()
    print(json.dumps(manifest, indent=2, sort_keys=True))
    print(f"MOVE_SPECIAL_EXECUTION_ORDER_DIGEST={digest}")

    required = {"pre_damage", "post_damage", "end_action"}
    missing = required - phases
    if missing:
        raise AssertionError(f"missing move-special phases in oracle callsites: {sorted(missing)}")
    if digest != EXPECTED_DIGEST:
        raise AssertionError(f"move-special execution order changed: expected {EXPECTED_DIGEST}, got {digest}")


if __name__ == "__main__":
    main()
