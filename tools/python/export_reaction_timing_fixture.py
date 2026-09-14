#!/usr/bin/env python3
import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    root = Path(args.source_root)
    fixture = root / "auto_ptu/data/campaigns/keyword_demos/interrupt-1_demo.json"
    data = json.loads(fixture.read_text(encoding="utf-8"))
    move = next(m for m in data["players"][0]["moves"] if m["name"] == "Attack of Opportunity")

    assert move["freq"] == "Free"
    assert move["priority"] == 0
    assert "interrupt-1" in move["keywords"]
    assert "as an Interrupt" in move["effects_text"]

    rows = [
        ("attack_of_opportunity", "interrupt-1", "INTERRUPT", "1", "false"),
        ("priority_control", "priority-20", "PRIORITY", "20", "false"),
        ("ordinary_control", "contact", "ORDINARY", "", "true"),
    ]
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "case\ttrait\ttiming\trank\tspends_ordinary_budget\n"
        + "".join("\t".join(row) + "\n" for row in rows),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
