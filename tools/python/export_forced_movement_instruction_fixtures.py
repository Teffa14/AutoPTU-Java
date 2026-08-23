#!/usr/bin/env python3
"""Export parity fixtures for move_traits.forced_movement_instruction()."""

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

    from auto_ptu.rules.move_traits import forced_movement_instruction

    cases = [
        ("none", [], ""),
        ("push_keyword", ["push"], ""),
        ("pull_keyword", ["pull"], ""),
        ("push_text_distance", [], "Push the target 3 meters."),
        ("pull_text_distance", [], "Pull target 2 squares."),
        ("push_zero_clamps", [], "Push 0 meters."),
        ("uppercase_text", [], "PULL the target 4 meters."),
        ("push_priority_keywords", ["pull", "push"], ""),
        ("push_priority_over_pull_text", ["pull"], "Push 5, then pull 2."),
        ("pushes_text", [], "Pushes the target 6 meters."),
        ("keyword_whitespace_not_trimmed", [" Push "], ""),
        ("keyword_substring_not_exact", ["superpush"], ""),
        ("description_substring_matches", [], "Repush 7 meters."),
        ("distance_defaults_without_number", [], "The target is pushed away."),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        handle.write("case\tkeywords\teffects_text\tkind\tdistance\n")
        for case, keywords, effects_text in cases:
            result = forced_movement_instruction({"keywords": keywords, "effects_text": effects_text})
            kind = "" if result is None else str(result["kind"])
            distance = "" if result is None else str(result["distance"])
            encoded_keywords = "|".join(keyword.replace("|", "") for keyword in keywords)
            encoded_text = effects_text.replace("\t", " ").replace("\n", " ")
            handle.write(f"{case}\t{encoded_keywords}\t{encoded_text}\t{kind}\t{distance}\n")


if __name__ == "__main__":
    main()
