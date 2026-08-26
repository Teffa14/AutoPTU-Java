#!/usr/bin/env python3
"""Freeze pinned-oracle Chronicler profile matching semantics."""
from __future__ import annotations

import argparse
import ast
from pathlib import Path


def find_helper(source_root: Path, helper_name: str) -> ast.FunctionDef:
    matches: list[ast.FunctionDef] = []
    for path in (source_root / "auto_ptu").rglob("*.py"):
        try:
            tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
        except (UnicodeDecodeError, SyntaxError):
            continue
        for node in ast.walk(tree):
            if isinstance(node, ast.FunctionDef) and node.name == helper_name:
                matches.append(node)
    if len(matches) != 1:
        raise RuntimeError(f"Expected one {helper_name} definition, found {len(matches)}")
    return matches[0]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    helper = find_helper(args.source_root, "_chronicler_profile_matches")
    text = ast.unparse(helper)
    parameters = [argument.arg for argument in helper.args.args]
    properties = {
        "helper_receives_trainer_and_target": parameters[-2:] == ["trainer_id", "target"],
        "missing_target_fails_closed": "if target is None" in text and "return False" in text,
        "uses_trainer_chronicler_metadata": "self._chronicler_metadata(trainer_id)" in text,
        "requires_profile_archive": "'profile' not in metadata.get('archives', set())" in text,
        "uses_profile_records": "metadata.get('records', {}).get('profile', [])" in text,
        "records_normalize_strip_lower": "str(entry or '').strip().lower()" in text,
        "empty_records_fail_closed": "if not record_keys" in text,
        "matches_target_name": "str(target.spec.name or '').strip().lower()" in text,
        "matches_target_species": "str(target.spec.species or '').strip().lower()" in text,
        "looks_up_controller_trainer": "self.trainers.get(target.controller_id)" in text,
        "matches_controller_trainer_name": "str(trainer.name or '').strip().lower()" in text,
        "controller_name_is_optional": "if trainer is not None" in text,
        "uses_any_nonempty_candidate": "any((candidate and candidate in record_keys for candidate in candidates))" in text
            or "any(candidate and candidate in record_keys for candidate in candidates)" in text,
    }

    failed = [name for name, value in properties.items() if not value]
    if failed:
        raise RuntimeError("Pinned Chronicler profile-match contract changed: " + ", ".join(failed))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "property\texpected\n" + "".join(f"{name}\t1\n" for name in properties),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
