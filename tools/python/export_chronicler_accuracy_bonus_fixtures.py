#!/usr/bin/env python3
"""Execute the pinned Python Chronicler Accuracy helper with controlled state."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path
from typing import Any


def find_helper(source_root: Path) -> ast.FunctionDef:
    matches: list[ast.FunctionDef] = []
    for path in (source_root / "auto_ptu").rglob("*.py"):
        try:
            tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
        except (UnicodeDecodeError, SyntaxError):
            continue
        for node in ast.walk(tree):
            if isinstance(node, ast.FunctionDef) and node.name == "_chronicler_accuracy_bonus":
                matches.append(node)
    if len(matches) != 1:
        raise RuntimeError(f"Expected one _chronicler_accuracy_bonus definition, found {len(matches)}")
    return matches[0]


def load_helper(source_root: Path):
    helper = find_helper(source_root)
    module = ast.Module(body=[helper], type_ignores=[])
    ast.fix_missing_locations(module)
    namespace: dict[str, Any] = {}
    exec(compile(module, "<chronicler_accuracy_bonus>", "exec"), namespace)
    return namespace["_chronicler_accuracy_bonus"]


class FakeAttacker:
    def __init__(self, controller_id: str, entries: list[dict[str, Any]]) -> None:
        self.controller_id = controller_id
        self.temporary_effects = entries

    def get_temporary_effects(self, name: str):
        if name != "targeted_profiling":
            return []
        return list(self.temporary_effects)


class FakeBattle:
    def __init__(self, round_number: int, matching_controllers: set[str]) -> None:
        self.round = round_number
        self.matching_controllers = matching_controllers
        self.seen: list[str] = []

    def _chronicler_profile_matches(self, controller_id, defender) -> bool:
        key = str(controller_id)
        self.seen.append(key)
        return key in self.matching_controllers


def run_case(helper, name: str, round_number: int, controller_id: str, entries, matches):
    copied = [dict(entry) for entry in entries]
    battle = FakeBattle(round_number, set(matches))
    attacker = FakeAttacker(controller_id, copied)
    defender = object()
    bonus = int(helper(battle, attacker, defender) or 0)
    return name, bonus, len(attacker.temporary_effects), ",".join(battle.seen)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    helper = load_helper(args.source_root)
    cases = [
        ("baseline", 3, "trainer-a", [], set()),
        ("single_match", 3, "trainer-a", [{}], {"trainer-a"}),
        ("stacked_matches", 3, "trainer-a", [{}, {}], {"trainer-a"}),
        ("nonmatch", 3, "trainer-a", [{}], set()),
        ("fallback_controller_match", 3, "trainer-a", [{"source_controller": ""}], {"trainer-a"}),
        ("explicit_controller_match", 3, "trainer-a", [{"source_controller": "trainer-b"}], {"trainer-b"}),
        ("same_round_not_expired", 3, "trainer-a", [{"expires_round": 3}], {"trainer-a"}),
        ("next_round_expired", 4, "trainer-a", [{"expires_round": 3}], {"trainer-a"}),
        (
            "mixed_expired_and_live",
            4,
            "trainer-a",
            [{"expires_round": 2}, {"expires_round": 4}, {"source_controller": "trainer-b"}],
            {"trainer-a", "trainer-b"},
        ),
    ]
    rows = [run_case(helper, *case) for case in cases]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "case\tbonus\tremaining\tcontrollers_seen\n"
        + "".join(f"{name}\t{bonus}\t{remaining}\t{seen}\n" for name, bonus, remaining, seen in rows),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
