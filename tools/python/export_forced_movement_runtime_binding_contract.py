#!/usr/bin/env python3
"""Freeze pinned Python forced-movement callsites as a language-neutral inventory."""
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


def statement_context(node: ast.AST, parents: dict[ast.AST, ast.AST]) -> str:
    current: ast.AST | None = node
    while current is not None and not isinstance(current, ast.stmt):
        current = parents.get(current)
    if current is None:
        return ""
    rendered = " ".join(ast.unparse(current).split())
    return rendered[:240]


def production_calls(source_root: Path) -> list[tuple[str, str, int, str, str]]:
    package_root = source_root / "auto_ptu"
    if not package_root.is_dir():
        raise SystemExit(f"missing oracle package: {package_root}")

    calls: list[tuple[str, str, int, str, str]] = []
    for path in sorted(package_root.rglob("*.py")):
        tree = ast.parse(path.read_text(encoding="utf-8-sig"), filename=str(path))
        parents: dict[ast.AST, ast.AST] = {}
        for parent in ast.walk(tree):
            for child in ast.iter_child_nodes(parent):
                parents[child] = parent
        for node in ast.walk(tree):
            if isinstance(node, ast.Call) and call_symbol(node) == SYMBOL:
                relative = path.relative_to(source_root).as_posix()
                calls.append((
                    role_for(path, source_root),
                    relative,
                    node.lineno,
                    enclosing_name(node, parents),
                    statement_context(node, parents),
                ))
    return calls


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    calls = production_calls(args.source_root)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["role\tpath\tline\tenclosing\tstatement"]
    for role, path, line, enclosing, statement in calls:
        lines.append("\t".join((role, path, str(line), enclosing, statement.replace("\t", " "))))
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")

    runtime = [entry for entry in calls if entry[0] == "runtime"]
    tooling = [entry for entry in calls if entry[0] == "tooling"]
    other = [entry for entry in calls if entry[0] == "other"]
    if len(runtime) != 1 or len(tooling) != 1 or other:
        raise SystemExit(
            "pinned forced-movement callsite inventory changed; "
            f"runtime={runtime}, tooling={tooling}, other={other}"
        )


if __name__ == "__main__":
    main()
