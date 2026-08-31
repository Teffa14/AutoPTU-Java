#!/usr/bin/env python3
"""Freeze pinned Python forced-movement callsites and their local execution order."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path

SYMBOL = "forced_movement_instruction"


def call_symbol(node: ast.Call) -> str | None:
    func = node.func
    if isinstance(func, ast.Name):
        return func.id
    if isinstance(func, ast.Attribute):
        return func.attr
    return None


def role_for(path: Path, source_root: Path) -> str:
    relative = path.relative_to(source_root).as_posix()
    if relative.startswith("auto_ptu/tools/"):
        return "tooling"
    if relative.startswith("auto_ptu/rules/"):
        return "runtime"
    return "other"


def enclosing_name(node: ast.AST, parents: dict[ast.AST, ast.AST]) -> str:
    current = parents.get(node)
    while current is not None:
        if isinstance(current, (ast.FunctionDef, ast.AsyncFunctionDef)):
            return current.name
        current = parents.get(current)
    return "<module>"


def containing_statement(node: ast.AST, parents: dict[ast.AST, ast.AST]) -> ast.stmt | None:
    current: ast.AST | None = node
    while current is not None and not isinstance(current, ast.stmt):
        current = parents.get(current)
    return current if isinstance(current, ast.stmt) else None


def compact(node: ast.AST | None) -> str:
    if node is None:
        return ""
    return " ".join(ast.unparse(node).split()).replace("\t", " ")[:320]


def sibling_context(
    statement: ast.stmt | None,
    parents: dict[ast.AST, ast.AST],
) -> tuple[str, str, str]:
    if statement is None:
        return "", "", ""
    parent = parents.get(statement)
    if parent is None:
        return "", "", ""

    for field_name, value in ast.iter_fields(parent):
        if not isinstance(value, list) or statement not in value:
            continue
        index = value.index(statement)
        previous = value[index - 1] if index > 0 and isinstance(value[index - 1], ast.stmt) else None
        following = value[index + 1] if index + 1 < len(value) and isinstance(value[index + 1], ast.stmt) else None
        return field_name, compact(previous), compact(following)
    return "", "", ""


def production_calls(source_root: Path) -> list[tuple[str, str, int, str, str, str, str, str]]:
    package_root = source_root / "auto_ptu"
    if not package_root.is_dir():
        raise SystemExit(f"missing oracle package: {package_root}")

    calls: list[tuple[str, str, int, str, str, str, str, str]] = []
    for path in sorted(package_root.rglob("*.py")):
        tree = ast.parse(path.read_text(encoding="utf-8-sig"), filename=str(path))
        parents: dict[ast.AST, ast.AST] = {}
        for parent in ast.walk(tree):
            for child in ast.iter_child_nodes(parent):
                parents[child] = parent
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call) or call_symbol(node) != SYMBOL:
                continue
            statement = containing_statement(node, parents)
            block, previous, following = sibling_context(statement, parents)
            relative = path.relative_to(source_root).as_posix()
            calls.append((
                role_for(path, source_root),
                relative,
                node.lineno,
                enclosing_name(node, parents),
                compact(statement),
                block,
                previous,
                following,
            ))
    return calls


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    calls = production_calls(args.source_root)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["role\tpath\tline\tenclosing\tstatement\tblock\tprevious\tnext"]
    for entry in calls:
        lines.append("\t".join(str(value).replace("\t", " ") for value in entry))
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")

    runtime = [entry for entry in calls if entry[0] == "runtime"]
    tooling = [entry for entry in calls if entry[0] == "tooling"]
    other = [entry for entry in calls if entry[0] == "other"]
    if len(runtime) != 1 or len(tooling) != 1 or other:
        raise SystemExit(
            "pinned forced-movement callsite inventory changed; "
            f"runtime={runtime}, tooling={tooling}, other={other}"
        )

    runtime_entry = runtime[0]
    if not runtime_entry[5] or not runtime_entry[6] or not runtime_entry[7]:
        raise SystemExit(
            "runtime forced-movement callsite no longer has stable neighboring statements; "
            f"entry={runtime_entry}"
        )


if __name__ == "__main__":
    main()
