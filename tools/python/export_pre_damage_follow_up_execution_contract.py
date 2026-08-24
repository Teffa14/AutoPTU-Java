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
    interrupt_text, interrupt_tree = source(
        root / "auto_ptu/rules/hooks/abilities/pre_damage_interrupts.py"
    )
    battle_text, battle_tree = source(root / "auto_ptu/rules/battle_state.py")

    sway = segment(interrupt_text, function(interrupt_tree, "_sway_interrupt"))
    magic_coat = segment(interrupt_text, function(interrupt_tree, "_magic_coat_interrupt"))
    resolve_targets = segment(battle_text, function(battle_tree, "resolve_move_targets"))

    rows = {
        "sway_reenters_resolve_move_targets": int("ctx.battle.resolve_move_targets(" in sway),
        "magic_coat_reenters_resolve_move_targets": int("ctx.battle.resolve_move_targets(" in magic_coat),
        "sway_reuses_original_move": int("move=ctx.move" in sway),
        "magic_coat_reuses_original_move": int("move=ctx.move" in magic_coat),
        "sway_redirects_attacker_into_self": int(
            "attacker_id=ctx.attacker_id" in sway and "target_id=ctx.attacker_id" in sway
        ),
        "magic_coat_swaps_defender_into_attacker": int(
            "attacker_id=ctx.defender_id" in magic_coat and "target_id=ctx.attacker_id" in magic_coat
        ),
        "sway_follow_up_is_synchronous": int(
            sway.find("ctx.battle.resolve_move_targets(") < sway.find('remove_temporary_effect("sway_redirect")')
        ),
        "magic_coat_follow_up_is_synchronous": int(
            magic_coat.find("ctx.battle.resolve_move_targets(") < magic_coat.find('ctx.result["damage"] = 0')
        ),
        "sway_swallows_follow_up_errors": int("except Exception:" in sway and "pass" in sway),
        "magic_coat_swallows_follow_up_errors": int("except Exception:" in magic_coat and "pass" in magic_coat),
        "resolve_targets_does_not_mark_actions": int("mark_action(" not in resolve_targets),
        "resolve_targets_does_not_record_move_frequency": int(
            "record_move_use" not in resolve_targets
            and "record_move_used" not in resolve_targets
            and "_record_move_used" not in resolve_targets
        ),
        "resolve_targets_runs_pre_damage_interrupts": int('phase="pre_damage_interrupt"' in resolve_targets),
    }

    if not all(rows.values()):
        raise SystemExit(f"unexpected PRE-damage follow-up execution contract: {rows}")

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        "property\tvalue\n" + "".join(f"{key}\t{value}\n" for key, value in rows.items()),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
