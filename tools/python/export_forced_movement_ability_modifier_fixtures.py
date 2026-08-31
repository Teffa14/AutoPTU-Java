#!/usr/bin/env python3
"""Freeze the pinned Python Thrust forced-movement modifier as language-neutral fixtures."""
from __future__ import annotations

import argparse
import ast
import copy
from pathlib import Path
from types import SimpleNamespace


def _find_thrust_if(tree: ast.AST) -> ast.If:
    for node in ast.walk(tree):
        if not isinstance(node, ast.If):
            continue
        test = ast.unparse(node.test)
        if "has_ability('Thrust')" in test or 'has_ability("Thrust")' in test:
            return node
    raise RuntimeError("pinned oracle no longer contains the Thrust forced-movement branch")


def _compile_branch(node: ast.If):
    fn = ast.FunctionDef(
        name="apply_thrust",
        args=ast.arguments(
            posonlyargs=[],
            args=[ast.arg(arg="attacker"), ast.arg(arg="move"), ast.arg(arg="instruction")],
            kwonlyargs=[],
            kw_defaults=[],
            defaults=[],
        ),
        body=[copy.deepcopy(node), ast.Return(value=ast.Name(id="instruction", ctx=ast.Load()))],
        decorator_list=[],
    )
    module = ast.fix_missing_locations(ast.Module(body=[fn], type_ignores=[]))
    namespace: dict[str, object] = {}
    exec(compile(module, "<pinned-thrust-branch>", "exec"), namespace)
    return namespace["apply_thrust"]


class _Attacker:
    def __init__(self, enabled: bool):
        self.enabled = enabled

    def has_ability(self, name: str) -> bool:
        return self.enabled and name == "Thrust"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source = Path(args.source_root) / "auto_ptu" / "rules" / "battle_state.py"
    tree = ast.parse(source.read_text(encoding="utf-8"), filename=str(source))
    apply_thrust = _compile_branch(_find_thrust_if(tree))

    cases = [
        ("physical_none", True, "physical", None),
        ("physical_push", True, "physical", {"kind": "push", "distance": 2}),
        ("physical_pull", True, "physical", {"kind": "pull", "distance": 2}),
        ("special_none", True, "special", None),
        ("no_ability", False, "physical", None),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    rows = ["case\thas_thrust\tcategory\tbase_kind\tbase_distance\tresult_kind\tresult_distance"]
    for case, has_thrust, category, base in cases:
        result = apply_thrust(_Attacker(has_thrust), SimpleNamespace(category=category), copy.deepcopy(base))
        rows.append("\t".join([
            case,
            "1" if has_thrust else "0",
            category,
            "" if base is None else str(base.get("kind", "")),
            "" if base is None else str(base.get("distance", "")),
            "" if result is None else str(result.get("kind", "")),
            "" if result is None else str(result.get("distance", "")),
        ]))
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
