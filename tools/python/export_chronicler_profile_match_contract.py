#!/usr/bin/env python3
"""Inspect pinned-oracle Chronicler profile matching before freezing its Java contract."""
from __future__ import annotations

import argparse
import ast
import hashlib
import json
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

    function = find_helper(args.source_root, "_chronicler_profile_matches")
    text = ast.unparse(function)
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "digest\t" + digest + "\n" +
        "source\t" + json.dumps(text, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
