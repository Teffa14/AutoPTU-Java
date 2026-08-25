#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ast
import sys
from pathlib import Path
from types import SimpleNamespace


def _function(tree: ast.AST, name: str) -> ast.FunctionDef:
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef) and node.name == name:
            return node
    raise RuntimeError(f"missing Python oracle function: {name}")


def _move_result_carries_accuracy_roll(root: Path) -> bool:
    source = (root / "auto_ptu" / "rules" / "calculations.py").read_text(encoding="utf-8")
    function = _function(ast.parse(source), "resolve_move_action")
    return any(
        isinstance(node, ast.Dict)
        and any(
            key is None and isinstance(value, ast.Name) and value.id == "accuracy"
            for key, value in zip(node.keys, node.values)
        )
        for node in ast.walk(function)
    )


def _effect_roll_reads_shared_roll(root: Path) -> bool:
    source = (root / "auto_ptu" / "rules" / "hooks" / "move_specials.py").read_text(encoding="utf-8")
    function = _function(ast.parse(source), "_effect_roll")
    for node in ast.walk(function):
        if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Attribute):
            continue
        if node.func.attr != "get" or not node.args:
            continue
        owner = node.func.value
        if not (
            isinstance(owner, ast.Attribute)
            and owner.attr == "result"
            and isinstance(owner.value, ast.Name)
            and owner.value.id == "ctx"
        ):
            continue
        if isinstance(node.args[0], ast.Constant) and node.args[0].value == "roll":
            return True
    return False


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    root = Path(args.source_root).resolve()
    sys.path.insert(0, str(root))
    from auto_ptu.rules.hooks import move_specials

    class FakeMon:
        def __init__(self, *, abilities=(), features=(), effects=(), stages=None, hardened=0):
            self._abilities = set(abilities)
            self._features = set(features)
            self.temporary_effects = [dict(entry) for entry in effects]
            self.combat_stages = dict(stages or {})
            self._hardened = hardened

        def has_ability(self, name):
            return name in self._abilities

        def has_trainer_feature(self, name):
            return name in self._features

        def get_temporary_effects(self, kind):
            return [entry for entry in self.temporary_effects if entry.get("kind") == kind]

        def hardened_crit_effect_bonus(self, _battle):
            return self._hardened

    class FakeBattle:
        def __init__(self, *, penalty=0):
            self.round = 3
            self._penalty = penalty

        def abilities_suppressed_for(self, _actor_id):
            return False

        def _roll_penalty(self, _attacker):
            return self._penalty

    def move(*, name="Test", type="Normal", category="Physical", effects_text="", target_kind="Melee"):
        return SimpleNamespace(
            name=name,
            type=type,
            category=category,
            effects_text=effects_text,
            target_kind=target_kind,
            range_kind=target_kind,
            range_text=target_kind,
        )

    def run(
        name,
        *,
        base=10,
        attacker=None,
        defender=None,
        battle=None,
        move_obj=None,
    ):
        ctx = SimpleNamespace(
            attacker_id="actor",
            attacker=attacker or FakeMon(),
            defender=defender or FakeMon(),
            battle=battle or FakeBattle(),
            move=move_obj or move(),
            result={"roll": base},
        )
        return name, move_specials._effect_roll(ctx)

    scenarios = [
        run("baseline"),
        run("immutable", defender=FakeMon(effects=({"kind": "immutable_mind_block", "move": "Test", "expires_round": 3},))),
        run("range_block", attacker=FakeMon(effects=({"kind": "effect_range_block", "expires_round": 3},))),
        run("serene", attacker=FakeMon(abilities=("Serene Grace",))),
        run("stench", attacker=FakeMon(abilities=("Stench",)), move_obj=move(effects_text="Flinches on 18+.")),
        run("firebrand", attacker=FakeMon(features=("Firebrand",)), move_obj=move(type="Fire", effects_text="Burns on 18+.")),
        run("roll_penalty", battle=FakeBattle(penalty=3)),
        run("mindbreak", attacker=FakeMon(effects=({"kind": "mindbreak_bound"},)), move_obj=move(type="Psychic", category="Special")),
        run("polished", attacker=FakeMon(features=("Polished Shine",)), move_obj=move(type="Steel")),
        run("brutal", attacker=FakeMon(effects=({"kind": "brutal_training"},))),
        run("range_bonus", attacker=FakeMon(effects=(
            {"kind": "effect_range_bonus", "amount": 2, "expires_round": 3},
            {"kind": "effect_range_bonus", "amount": -1, "expires_round": 3},
        ))),
        run("stat_stratagem", attacker=FakeMon(
            effects=({"kind": "stat_stratagem", "stat": "spatk"},),
            stages={"spatk": 5},
        ), move_obj=move(category="Special", target_kind="Ranged")),
        run("stat_stratagem_stacked", attacker=FakeMon(
            effects=(
                {"kind": "stat_stratagem", "stat": "spatk"},
                {"kind": "stat_stratagem", "stat": "atk"},
                {"kind": "stat_stratagem", "stat": "spatk"},
            ),
            stages={"spatk": 5},
        ), move_obj=move(category="Special", target_kind="Ranged")),
        run("hardened", attacker=FakeMon(hardened=4)),
        ("move_result_carries_accuracy_roll", int(_move_result_carries_accuracy_roll(root))),
        ("effect_roll_reads_shared_roll", int(_effect_roll_reads_shared_roll(root))),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("".join(f"{name}\t{value}\n" for name, value in scenarios), encoding="utf-8")


if __name__ == "__main__":
    main()
