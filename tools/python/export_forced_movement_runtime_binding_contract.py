#!/usr/bin/env python3
"""Freeze pinned Python forced-movement callsites, dataflow, and runtime consumption order."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path

SYMBOL = "forced_movement_instruction"
CONSUMER_SYMBOL = "apply_forced_movement"


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


def enclosing_function(node: ast.AST, parents: dict[ast.AST, ast.AST]) -> ast.FunctionDef | ast.AsyncFunctionDef | None:
    current = parents.get(node)
    while current is not None:
        if isinstance(current, (ast.FunctionDef, ast.AsyncFunctionDef)):
            return current
        current = parents.get(current)
    return None


def enclosing_name(node: ast.AST, parents: dict[ast.AST, ast.AST]) -> str:
    function = enclosing_function(node, parents)
    return function.name if function is not None else "<module>"


def containing_statement(node: ast.AST, parents: dict[ast.AST, ast.AST]) -> ast.stmt | None:
    current: ast.AST | None = node
    while current is not None and not isinstance(current, ast.stmt):
        current = parents.get(current)
    return current if isinstance(current, ast.stmt) else None


def enclosing_if(node: ast.AST, parents: dict[ast.AST, ast.AST]) -> ast.If | None:
    current = parents.get(node)
    while current is not None:
        if isinstance(current, ast.If):
            return current
        if isinstance(current, (ast.FunctionDef, ast.AsyncFunctionDef)):
            return None
        current = parents.get(current)
    return None


def compact(node: ast.AST | None, limit: int = 320) -> str:
    if node is None:
        return ""
    return " ".join(ast.unparse(node).split()).replace("\t", " ")[:limit]


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
        return field_name, compact(previous, 700), compact(following, 700)
    return "", "", ""


def instruction_trace(function: ast.FunctionDef | ast.AsyncFunctionDef | None) -> list[tuple[int, str, str]]:
    if function is None:
        return []
    rows: list[tuple[int, str, str]] = []
    for statement in ast.walk(function):
        if not isinstance(statement, ast.stmt):
            continue
        names = [node for node in ast.walk(statement) if isinstance(node, ast.Name) and node.id == "instruction"]
        if not names:
            continue
        contexts = sorted({type(node.ctx).__name__.removesuffix("Context") for node in names})
        rows.append((getattr(statement, "lineno", -1), "+".join(contexts), compact(statement, 500)))
    return sorted(set(rows), key=lambda row: (row[0], row[2]))


def instruction_consumers(
    function: ast.FunctionDef | ast.AsyncFunctionDef | None,
    parents: dict[ast.AST, ast.AST],
    relative: str,
) -> list[tuple[str, int, str, str, str, str, str, str]]:
    if function is None:
        return []
    rows: list[tuple[str, int, str, str, str, str, str, str]] = []
    for node in ast.walk(function):
        if not isinstance(node, ast.Call) or call_symbol(node) != CONSUMER_SYMBOL:
            continue
        if not any(isinstance(child, ast.Name) and child.id == "instruction" for child in ast.walk(node)):
            continue
        statement = containing_statement(node, parents)
        guard_node = enclosing_if(node, parents)
        # The call is the only statement in its guard body. Freeze siblings of the
        # guard itself so the fixture captures the real pipeline landmarks around
        # forced movement rather than two empty inner-body neighbors.
        ordering_anchor = guard_node if guard_node is not None else statement
        block, previous, following = sibling_context(ordering_anchor, parents)
        rows.append((
            relative,
            node.lineno,
            function.name,
            compact(guard_node.test if guard_node is not None else None, 500),
            compact(statement, 500),
            block,
            previous,
            following,
        ))
    return sorted(set(rows), key=lambda row: (row[1], row[4]))


def production_calls(source_root: Path) -> tuple[
    list[tuple[str, str, int, str, str, str, str, str]],
    list[tuple[str, int, str, str]],
    list[tuple[str, int, str, str, str, str, str, str]],
]:
    package_root = source_root / "auto_ptu"
    if not package_root.is_dir():
        raise SystemExit(f"missing oracle package: {package_root}")

    calls: list[tuple[str, str, int, str, str, str, str, str]] = []
    trace_rows: list[tuple[str, int, str, str]] = []
    consumer_rows: list[tuple[str, int, str, str, str, str, str, str]] = []
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
            role = role_for(path, source_root)
            calls.append((
                role,
                relative,
                node.lineno,
                enclosing_name(node, parents),
                compact(statement),
                block,
                previous,
                following,
            ))
            if role == "runtime":
                function = enclosing_function(node, parents)
                for line, contexts, rendered in instruction_trace(function):
                    trace_rows.append((relative, line, contexts, rendered))
                consumer_rows.extend(instruction_consumers(function, parents, relative))
    return calls, trace_rows, consumer_rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--trace-output", type=Path)
    parser.add_argument("--consumer-output", type=Path)
    args = parser.parse_args()

    calls, trace_rows, consumer_rows = production_calls(args.source_root)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["role\tpath\tline\tenclosing\tstatement\tblock\tprevious\tnext"]
    for entry in calls:
        lines.append("\t".join(str(value).replace("\t", " ") for value in entry))
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")

    if args.trace_output is not None:
        args.trace_output.parent.mkdir(parents=True, exist_ok=True)
        trace_lines = ["path\tline\tcontexts\tstatement"]
        trace_lines.extend("\t".join(str(value).replace("\t", " ") for value in row) for row in trace_rows)
        args.trace_output.write_text("\n".join(trace_lines) + "\n", encoding="utf-8")

    if args.consumer_output is not None:
        args.consumer_output.parent.mkdir(parents=True, exist_ok=True)
        consumer_lines = ["path\tline\tenclosing\tguard\tstatement\tordering_block\tprevious\tnext"]
        consumer_lines.extend("\t".join(str(value).replace("\t", " ") for value in row) for row in consumer_rows)
        args.consumer_output.write_text("\n".join(consumer_lines) + "\n", encoding="utf-8")

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
    if not trace_rows:
        raise SystemExit("runtime forced-movement instruction dataflow trace is empty")
    if len(consumer_rows) != 1:
        raise SystemExit(f"runtime forced-movement consumer inventory changed: {consumer_rows}")

    consumer = consumer_rows[0]
    guard = consumer[3]
    statement = consumer[4]
    if "instruction" not in guard or "hit" not in guard:
        raise SystemExit(f"forced movement consumer is no longer hit-gated by instruction presence: {consumer}")
    if CONSUMER_SYMBOL not in statement or "instruction" not in statement:
        raise SystemExit(f"forced movement consumer statement changed: {consumer}")
    if not consumer[5] or not consumer[6] or not consumer[7]:
        raise SystemExit(
            "forced movement consumer no longer has stable outer pipeline landmarks; "
            f"consumer={consumer}"
        )


if __name__ == "__main__":
    main()
