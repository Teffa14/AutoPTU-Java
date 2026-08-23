#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def function(tree: ast.AST, name: str) -> ast.FunctionDef:
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef) and node.name == name:
            return node
    raise SystemExit(f"missing function: {name}")


def source(path: Path) -> tuple[str, ast.Module]:
    text = path.read_text(encoding="utf-8")
    return text, ast.parse(text)


def segment(text: str, node: ast.AST) -> str:
    value = ast.get_source_segment(text, node)
    if value is None:
        raise SystemExit("could not recover source segment")
    return value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    root = Path(args.source_root)
    interrupt_path = root / "auto_ptu/rules/hooks/abilities/pre_damage_interrupts.py"
    registry_path = root / "auto_ptu/rules/hooks/ability_hooks.py"
    interrupt_text, interrupt_tree = source(interrupt_path)
    registry_text, registry_tree = source(registry_path)

    allow = segment(interrupt_text, function(interrupt_tree, "_allow_out_of_turn"))
    telepathy = segment(interrupt_text, function(interrupt_tree, "_telepathy_interrupt"))
    apply_hooks = segment(registry_text, function(registry_tree, "apply_ability_hooks"))

    required_payload = (
        '"actor_id": actor_id', '"label": label', '"phase": "pre_damage_interrupt"',
        '"move": move_name', '"trigger_move": move_name', '"attacker_id": ctx.attacker_id',
        '"defender_id": ctx.defender_id', '"optional": optional'
    )
    rows = {
        "missing_decision_callback_allows": int("return True" in allow and "should_trigger_out_of_turn" in allow),
        "decision_payload_complete": int(all(token in allow for token in required_payload)),
        "telepathy_uses_optional_decision": int('_allow_out_of_turn(ctx, defender_id, "Telepathy", optional=True)' in telepathy),
        "telepathy_cancels_hit": int("ctx.hit = False" in telepathy),
        "telepathy_zeroes_damage": int("ctx.damage = 0" in telepathy),
        "telepathy_zeroes_type_multiplier": int("ctx.result[\"type_multiplier\"] = 0.0" in telepathy),
        "ability_registry_continues_after_hook": int("func(ctx)" in apply_hooks and "for ability, holder, func in hooks:" in apply_hooks),
    }
    if not all(rows.values()):
        raise SystemExit(f"unexpected pre-damage reaction contract: {rows}")

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("property\tvalue\n" + "".join(f"{k}\t{v}\n" for k, v in rows.items()), encoding="utf-8")


if __name__ == "__main__":
    main()
