#!/usr/bin/env python3
import argparse
import json
from pathlib import Path


def normalize(value: str) -> str:
    return "_".join(part for part in "".join(ch.lower() if ch.isalnum() else " " for ch in value).split())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    scenario = Path(args.source_root) / "auto_ptu/data/campaigns/keyword_demos/interrupt-1_demo.json"
    data = json.loads(scenario.read_text(encoding="utf-8"))

    rows = ["combatant_id\treaction_key\tmove_ids\texpected_status"]
    for side in ("players", "foes"):
        for combatant in data[side]:
            combatant_id = normalize(combatant["name"])
            move_ids = [move["name"] for move in combatant.get("moves", [])]
            owns = any(normalize(move_id) == "attack_of_opportunity" for move_id in move_ids)
            rows.append(
                f"{combatant_id}\tattack_of_opportunity\t{','.join(move_ids)}\t{'OWNED' if owns else 'NOT_OWNED'}"
            )

    if not any(row.endswith("\tOWNED") for row in rows[1:]):
        raise SystemExit("Pinned oracle no longer grants Attack of Opportunity through a moveset")
    if not any(row.endswith("\tNOT_OWNED") for row in rows[1:]):
        raise SystemExit("Pinned oracle fixture lacks a negative ownership control")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
