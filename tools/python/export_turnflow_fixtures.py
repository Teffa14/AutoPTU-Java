#!/usr/bin/env python3
"""Extract action and phase declarations from Python battle_state.py without importing it."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def enum_values(tree: ast.Module, class_name: str) -> list[tuple[str, str]]:
    for node in tree.body:
        if isinstance(node, ast.ClassDef) and node.name == class_name:
            result: list[tuple[str, str]] = []
            for statement in node.body:
                if not isinstance(statement, ast.Assign) or len(statement.targets) != 1:
                    continue
                target = statement.targets[0]
                if isinstance(target, ast.Name) and isinstance(statement.value, ast.Constant) and isinstance(statement.value.value, str):
                    result.append((target.id, statement.value.value))
            return result
    raise RuntimeError(f"class not found: {class_name}")


def phase_sequence(tree: ast.Module) -> list[str]:
    for node in tree.body:
        if isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name) and node.target.id == "_PHASE_SEQUENCE":
            value = node.value
            if not isinstance(value, ast.Tuple):
                raise RuntimeError("_PHASE_SEQUENCE is not a tuple")
            result: list[str] = []
            for entry in value.elts:
                if isinstance(entry, ast.Attribute) and isinstance(entry.value, ast.Name) and entry.value.id == "TurnPhase":
                    result.append(entry.attr)
                else:
                    raise RuntimeError(f"unsupported phase entry: {ast.dump(entry)}")
            return result
    raise RuntimeError("_PHASE_SEQUENCE not found")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    path = args.source_root.resolve() / "auto_ptu" / "rules" / "battle_state.py"
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))

    scenarios: list[tuple[str, str]] = []
    for name, value in enum_values(tree, "ActionType"):
        scenarios.append((f"action_{name}", value))
    for name, value in enum_values(tree, "TurnPhase"):
        scenarios.append((f"phase_{name}", value))
    scenarios.append(("phase_sequence", ",".join(phase_sequence(tree))))

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(f"{name}\t{value}" for name, value in scenarios) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(scenarios)} Python turn-flow fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
