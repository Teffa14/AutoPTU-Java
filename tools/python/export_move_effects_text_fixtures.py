#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import sys
from pathlib import Path
from types import SimpleNamespace


def encode(value: str) -> str:
    return base64.b64encode(value.encode("utf-8")).decode("ascii")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    root = Path(args.source_root).resolve()
    sys.path.insert(0, str(root))
    from auto_ptu.rules.hooks import move_specials

    original_lookup = move_specials._lookup_move_spec
    fallback_by_name = {
        "fallback": SimpleNamespace(effects_text="Fallback burns on 18+."),
        "empty-fallback": SimpleNamespace(effects_text=""),
    }

    def fake_lookup(name: str):
        return fallback_by_name.get(str(name or "").strip().lower())

    move_specials._lookup_move_spec = fake_lookup
    try:
        scenarios = [
            ("direct", SimpleNamespace(name="Fallback", effects_text="Direct flinches on 17+.")),
            ("direct_whitespace", SimpleNamespace(name="Fallback", effects_text="  Direct text  ")),
            ("fallback", SimpleNamespace(name="Fallback", effects_text="")),
            ("missing", SimpleNamespace(name="Missing", effects_text="")),
            ("empty_fallback", SimpleNamespace(name="Empty-Fallback", effects_text="")),
        ]
        rows = [
            (name, move_specials._effects_text_for(move))
            for name, move in scenarios
        ]
    finally:
        move_specials._lookup_move_spec = original_lookup

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("".join(f"{name}\t{encode(value)}\n" for name, value in rows), encoding="utf-8")


if __name__ == "__main__":
    main()
