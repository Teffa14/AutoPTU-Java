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
    frozen = json.dumps(move, ensure_ascii=False).lower()

    for token in ("once per round", "sleeping", "flinched", "paralyzed"):
        if token not in frozen:
            raise SystemExit(f"Pinned Attack of Opportunity eligibility contract missing: {token}")

    rows = [
        "owns_reaction\tstatuses\tuses_this_round\texpected_reason",
        "true\t\t0\tELIGIBLE",
        "false\t\t0\tMISSING_OWNERSHIP",
        "true\tsleeping\t0\tBLOCKED_BY_STATUS",
        "true\tflinched\t0\tBLOCKED_BY_STATUS",
        "true\tparalyzed\t0\tBLOCKED_BY_STATUS",
        "true\tburned\t0\tELIGIBLE",
        "true\t\t1\tROUND_USE_EXHAUSTED",
    ]
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
