#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def source_segment(text: str, tree: ast.AST, name: str) -> str:
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef) and node.name == name:
            value = ast.get_source_segment(text, node)
            if value is None:
                raise SystemExit(f"could not recover source for {name}")
            return value
    raise SystemExit(f"missing function: {name}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    path = Path(args.source_root) / "auto_ptu/rules/hooks/abilities/pre_damage_interrupts.py"
    text = path.read_text(encoding="utf-8")
    fn = source_segment(text, ast.parse(text), "_shell_shield_interrupt")

    decision = fn.find('_allow_out_of_turn(ctx, ctx.defender_id, "Shell Shield", optional=True)')
    consume_ready = fn.find('remove_temporary_effect("shell_shield_ready")')
    add_withdrawn = fn.find('ctx.defender.statuses.append({"name": "Withdrawn"})')
    stage_mutation = fn.find('ctx.battle._apply_combat_stage(')
    ability_event = fn.find('"effect": "withdraw"')

    rows = {
        "requires_shell_shield_ready": int('get_temporary_effects("shell_shield_ready")' in fn),
        "decision_precedes_ready_consumption": int(decision >= 0 and consume_ready > decision),
        "declined_decision_preserves_ready": int(decision >= 0 and consume_ready > decision),
        "ready_payload_controls_event_ability_name": int(
            'ability_name = str(ready.get("ability") or "Shell Shield")' in fn
        ),
        "adds_withdrawn_only_when_missing": int(
            'if not ctx.defender.has_status("Withdrawn")' in fn and add_withdrawn >= 0
        ),
        "raises_defense_on_self_by_one": int(
            'attacker_id=ctx.defender_id' in fn
            and 'target_id=ctx.defender_id' in fn
            and 'stat="def"' in fn
            and 'delta=1' in fn
            and 'effect="shell_shield"' in fn
        ),
        "combat_stage_mutation_precedes_ability_event": int(
            stage_mutation >= 0 and ability_event > stage_mutation
        ),
        "emits_withdraw_ability_event": int(
            '"type": "ability"' in fn
            and '"actor": ctx.defender_id' in fn
            and '"target": ctx.attacker_id' in fn
            and '"ability": ability_name' in fn
            and '"effect": "withdraw"' in fn
        ),
        "does_not_cancel_hit": int('ctx.result["hit"] = False' not in fn),
        "does_not_zero_damage": int('ctx.result["damage"] = 0' not in fn),
        "does_not_zero_type_multiplier": int('ctx.result["type_multiplier"] = 0.0' not in fn),
    }
    if not all(rows.values()):
        raise SystemExit(f"unexpected Shell Shield contract: {rows}")

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        "property\tvalue\n" + "".join(f"{key}\t{value}\n" for key, value in rows.items()),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
