#!/usr/bin/env python3
"""Export parity fixtures for Python move_traits.move_has_keyword()."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.data_models import MoveSpec
    from auto_ptu.rules.move_traits import move_has_keyword

    cases = [
        ("exact", ["Aura", "Contact"], "Aura"),
        ("case_and_spacing", [" Aura ", "CONTACT"], " aUrA "),
        ("multiple", ["Push", "Pull", "Aura"], "pull"),
        ("substring_is_not_keyword", ["Aura Storm"], "Aura"),
        ("blank_entries_ignored", ["", "  ", "Push"], "push"),
        ("missing", ["Contact", "Punch"], "Aura"),
        ("empty_keywords", [], "Aura"),
        ("empty_query", ["Aura"], ""),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        handle.write("case\tkeywords\tquery\texpected\n")
        for case, keywords, query in cases:
            move = MoveSpec(name="Fixture", type="Normal", keywords=keywords)
            expected = 1 if move_has_keyword(move, query) else 0
            encoded_keywords = "|".join(keyword.replace("|", "") for keyword in keywords)
            handle.write(f"{case}\t{encoded_keywords}\t{query}\t{expected}\n")


if __name__ == "__main__":
    main()
