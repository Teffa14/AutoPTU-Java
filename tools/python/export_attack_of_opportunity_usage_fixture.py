#!/usr/bin/env python3
import argparse
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    scenario = Path(args.source_root) / "auto_ptu/data/campaigns/keyword_demos/interrupt-1_demo.json"
    data = json.loads(scenario.read_text(encoding="utf-8"))
    move = next(move for player in data["players"] for move in player["moves"] if move["name"] == "Attack of Opportunity")
    text = move["effects_text"].lower()
    if "only once per round" not in text:
        raise SystemExit("Pinned Attack of Opportunity contract no longer says only once per round")

    rows = [
        "operation\tcombatant\treaction_key\tround\texpected_uses",
        "query\talpha\tattack_of_opportunity\t3\t0",
        "record\talpha\tattack_of_opportunity\t3\t1",
        "query\tbeta\tattack_of_opportunity\t3\t0",
        "query\talpha\tother_reaction\t3\t0",
        "prune\t_\t_\t4\t0",
        "query\talpha\tattack_of_opportunity\t4\t0",
        "record\talpha\tattack_of_opportunity\t4\t1",
    ]
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
