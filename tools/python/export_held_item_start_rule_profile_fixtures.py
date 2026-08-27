#!/usr/bin/env python3
"""Export the supported generic held-item START rule profile from the pinned Python oracle."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


def _encode_pairs(values: object) -> str:
    if not values:
        return "-"
    return ";".join(f"{row[0]}:{row[1]}" for row in values)


def _encode_typed(value: object) -> str:
    if not value:
        return "-"
    return f"{value[0]}:{value[1]}"


def _value(value: object) -> str:
    return "-" if value is None else str(value)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = str(Path(args.source_root).resolve())
    sys.path.insert(0, source_root)
    from auto_ptu.rules.item_catalog import ItemEntry
    from auto_ptu.rules.item_effects import parse_item_effects

    scenarios = [
        ("baseline", ""),
        ("base_pair", "Improves your base Attack by 5 and base Special Defense by 3."),
        ("base_lower", "Lowers your base Speed by 4."),
        ("def_spdef_scalar", "Base DEF and SPDEF are increased by 20%."),
        ("atk_spatk_scalar", "Base ATK and SPATK are increased by 25%."),
        ("single_scalar", "Base Defense is increased by 30%."),
        ("accuracy_percent", "Accuracy of the user's attacks is increased by +15%."),
        ("accuracy_precedence", "Accuracy of the user's attacks is increased by +10%. Grants +5 bonus to all accuracy rolls."),
        ("accuracy_label", "Accuracy +4."),
        ("accuracy_flat", "The holder gains +7 Accuracy."),
        ("lower_av", "The holder gains +7 Accuracy on attacks targeting creatures with a lower Action Value."),
        ("typed_accuracy", "Increases the power and accuracy of Fire attacks by 15%."),
        ("status_evasion", "Speed Evasion +2."),
        ("all_evasion", "All Stat Evasions +3."),
        ("generic_evasion", "Evasion +4."),
        ("status_evasion_precedence", "Speed Evasion +2 and Evasion +5."),
        ("initiative", "Adds +10 to their Initiative."),
        ("speed_scalar", "The holder's Speed stat is halved."),
        (
            "combined",
            "Base Attack by +5. Base Defense is increased by 20%. Accuracy +2. "
            "The holder gains +6 Accuracy on attacks targeting creatures with a lower Action Value. "
            "Increases the power and accuracy of Water attacks by 10%. All Stat Evasions +1. "
            "Adds +10 to their Initiative. The holder's Speed stat is halved.",
        ),
    ]

    lines = []
    for name, description in scenarios:
        effects = parse_item_effects(ItemEntry(name=f"Fixture {name}", description=description))
        row = [
            name,
            _encode_pairs(effects.get("base_stat_changes")),
            _encode_pairs(effects.get("base_stat_scalars")),
            _value(effects.get("accuracy_bonus")),
            _value(effects.get("accuracy_bonus_vs_lower_av")),
            _encode_typed(effects.get("type_accuracy_bonus")),
            _value(effects.get("evasion_bonus_spd")),
            _value(effects.get("evasion_bonus_all")),
            _value(effects.get("initiative_bonus")),
            _value(effects.get("speed_scalar")),
            description.replace("\t", " ").replace("\n", " "),
        ]
        lines.append("\t".join(row))

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
