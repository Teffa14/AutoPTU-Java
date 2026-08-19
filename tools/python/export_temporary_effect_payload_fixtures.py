#!/usr/bin/env python3
"""Freeze representative Python temporary-effect payloads used by reusable move helpers."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path


class CaptureTarget:
    def __init__(self) -> None:
        self.entries: list[tuple[str, dict[str, object]]] = []

    def add_temporary_effect(self, name: str, **payload: object) -> None:
        self.entries.append((name, dict(payload)))


def emit(output: Path, scenario: str, name: str, payload: dict[str, object]) -> None:
    fields = [scenario, name]
    for key in sorted(payload):
        value = payload[key]
        fields.append(f"{key}={value}")
    with output.open("a", encoding="utf-8") as handle:
        handle.write("\t".join(fields) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    sys.path.insert(0, str(source_root))
    from auto_ptu.rules.hooks.move_effect_tools import (  # noqa: PLC0415
        apply_follow_me,
        disable_ability,
        disable_items,
    )

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("", encoding="utf-8")

    target = CaptureTarget()
    apply_follow_me(target, expires_round=4)
    name, payload = target.entries.pop()
    emit(output, "follow_me", name, payload)

    disable_items(target, expires_round=5)
    name, payload = target.entries.pop()
    emit(output, "items_disabled", name, payload)

    disable_ability(target, "Levitate", expires_round=6)
    name, payload = target.entries.pop()
    emit(output, "ability_disabled", name, payload)

    print(f"wrote 3 Python temporary-effect payload fixtures to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
