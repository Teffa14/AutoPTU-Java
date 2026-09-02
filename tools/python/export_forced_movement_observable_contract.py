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


def call_contains_string_literal(call: ast.Call, value: str) -> bool:
    return any(
        isinstance(node, ast.Constant) and isinstance(node.value, str) and node.value == value
        for node in ast.walk(call)
    )


def ability_semantic_calls(node: ast.AST) -> list[ast.Call]:
    return [
        call for call in ast.walk(node)
        if isinstance(call, ast.Call)
        and not call_symbol(call).endswith("has_ability")
        and call_contains_string_literal(call, "ability")
    ]


def require_scoped_ability_payload(
    fn: ast.AST,
    *,
    label: str,
    branch_fragments: tuple[str, ...],
    forbidden_branch_fragments: tuple[str, ...],
    event_fragments: tuple[str, ...],
) -> None:
    """Require an Ability semantic payload inside its own prevention control-flow scope."""
    scope_types = (ast.If, ast.For, ast.While)
    candidates: list[ast.AST] = []
    for node in ast.walk(fn):
        if not isinstance(node, scope_types):
            continue
        scope_text = compact(node)
        if not all(fragment in scope_text for fragment in branch_fragments):
            continue
        if any(fragment in scope_text for fragment in forbidden_branch_fragments):
            continue
        calls = ability_semantic_calls(node)
        if not calls:
            continue
        event_text = " ".join(compact(call) for call in calls)
        if all(fragment in event_text for fragment in event_fragments):
            candidates.append(node)

    if not candidates:
        raise SystemExit(
            f"apply_forced_movement {label} ability semantic payload drifted from its pinned branch"
        )


def require_insectoid_feature_event_contract(fn: ast.AST) -> None:
    """Freeze the Python semantic-event obligation for Insectoid Utility push prevention."""
    function_text = compact(fn)
    missing_rule_fragments = [
        fragment for fragment in ("Insectoid Utility", "Wallclimber")
        if fragment not in function_text
    ]
    if missing_rule_fragments:
        raise SystemExit(
            "apply_forced_movement lost the pinned Insectoid Utility prevention contract: "
            + ", ".join(missing_rule_fragments)
        )

    semantic_event_calls = [
        call for call in ast.walk(fn)
        if isinstance(call, ast.Call)
        and not call_symbol(call).endswith("has_trainer_feature")
        and call_contains_string_literal(call, "trainer_feature")
    ]
    if not semantic_event_calls:
        raise SystemExit(
            "apply_forced_movement lost the pinned trainer_feature semantic-event discriminator"
        )


def require_ability_prevention_event_contract(fn: ast.AST) -> None:
    """Freeze Ability-family prevention branches and exact observable semantic payloads."""
    function_text = compact(fn)
    missing_rule_fragments = [
        fragment for fragment in ("push_immunity", "Suction Cups", "Sumo Stance")
        if fragment not in function_text
    ]
    if missing_rule_fragments:
        raise SystemExit(
            "apply_forced_movement lost pinned Ability-family prevention branches: "
            + ", ".join(missing_rule_fragments)
        )

    semantic_event_calls = ability_semantic_calls(fn)
    if not semantic_event_calls:
        raise SystemExit(
            "apply_forced_movement lost the pinned ability semantic-event discriminator"
        )

    common_payload = (
        "'actor': target_id",
        "'target': attacker_id",
        "'effect': 'forced_movement_block'",
        "'target_hp': target.hp",
    )
    require_scoped_ability_payload(
        fn,
        label="push_immunity",
        branch_fragments=("push_immunity",),
        forbidden_branch_fragments=("Suction Cups", "Sumo Stance"),
        event_fragments=common_payload + (
            "'ability': source",
            "f'{source} prevents push effects.'",
        ),
    )
    require_scoped_ability_payload(
        fn,
        label="Suction Cups",
        branch_fragments=("Suction Cups", "Suction Cups [Errata]"),
        forbidden_branch_fragments=("Sumo Stance", "push_immunity"),
        event_fragments=common_payload + (
            "'ability': ability_name",
            "'Suction Cups prevents forced movement.'",
        ),
    )
    require_scoped_ability_payload(
        fn,
        label="Sumo Stance",
        branch_fragments=("Sumo Stance", "Sumo Stance [Errata]"),
        forbidden_branch_fragments=("Suction Cups", "push_immunity"),
        event_fragments=common_payload + (
            "'ability': ability_name",
            "'Sumo Stance prevents push effects.'",
        ),
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
    require_insectoid_feature_event_contract(fn)
    require_ability_prevention_event_contract(fn)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["path\tfunction\tline\tkind\tsymbol\tstatement"]
    for line, kind, symbol, statement in rows:
        lines.append("\t".join((SOURCE.as_posix(), FUNCTION, str(line), kind, symbol, statement)))
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
