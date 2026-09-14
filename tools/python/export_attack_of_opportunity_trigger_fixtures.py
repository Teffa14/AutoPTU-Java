#!/usr/bin/env python3
import argparse
import json
from pathlib import Path

TRIGGERS = [
    ("MANEUVER", "Push", "false", "false", "false", "false", "ADJACENT_FOE_USES_NON_TARGETING_MANEUVER", "An adjacent foe uses a Push, Grapple, Disarm, Trip, or Dirty Trick Maneuver that does not target you."),
    ("STAND_UP", "", "false", "false", "false", "false", "ADJACENT_FOE_STANDS_UP", "An adjacent foe stands up."),
    ("RANGED_ATTACK", "", "false", "false", "false", "false", "ADJACENT_FOE_USES_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET", "An adjacent foe uses a Ranged Attack that does not target someone adjacent to it."),
    ("ITEM_RETRIEVAL", "", "false", "false", "true", "false", "ADJACENT_FOE_RETRIEVES_ITEM", "An adjacent foe uses a Standard Action to pick up or retrieve an item."),
    ("SHIFT", "", "false", "false", "false", "true", "ADJACENT_FOE_SHIFTS_AWAY", "An adjacent foe Shifts out of a Square adjacent to you."),
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    scenario = Path(args.source_root) / "auto_ptu/data/campaigns/keyword_demos/interrupt-1_demo.json"
    data = json.loads(scenario.read_text(encoding="utf-8"))
    move = next(move for player in data["players"] for move in player["moves"] if move["name"] == "Attack of Opportunity")
    effects = move["effects_text"]

    rows = ["kind\taction_name\ttargets_reactor\tranged_targets_adjacent\tstandard_action\tshifts_away\texpected_trigger"]
    for kind, action_name, targets_reactor, ranged_adjacent, standard_action, shifts_away, trigger, phrase in TRIGGERS:
        if phrase not in effects:
            raise SystemExit(f"Pinned Attack of Opportunity contract missing: {phrase}")
        rows.append("\t".join([kind, action_name, targets_reactor, ranged_adjacent, standard_action, shifts_away, trigger]))

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
