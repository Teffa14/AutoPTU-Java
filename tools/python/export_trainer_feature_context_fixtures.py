#!/usr/bin/env python3
from __future__ import annotations

import argparse
import random
import sys
from pathlib import Path
from types import SimpleNamespace


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    source_root = Path(args.source_root).resolve()
    sys.path.insert(0, str(source_root))

    from auto_ptu.rules.trainer_features import TrainerFeatureDispatcher

    trainer_a = SimpleNamespace(feature_usage={})
    trainer_b = SimpleNamespace(feature_usage={})
    mon_a = SimpleNamespace(controller_id="trainer-a", active=True)
    mon_a_inactive = SimpleNamespace(controller_id="trainer-a", active=False)
    mon_b = SimpleNamespace(controller_id="trainer-b", active=True)

    def evaluate(
        name: str,
        conditions,
        *,
        actor_id="mon-a",
        pokemon=None,
        phase="ACTION",
        round_number=3,
        payload=None,
        usage=None,
        seed=2026,
    ):
        pokemon_map = {
            "mon-a": mon_a,
            "mon-a-inactive": mon_a_inactive,
            "mon-b": mon_b,
        }
        if pokemon is not None:
            pokemon_map = pokemon
        trainer_a.feature_usage = dict(usage or {})
        rng = random.Random(seed)
        battle = SimpleNamespace(
            trainers={"trainer-a": trainer_a, "trainer-b": trainer_b},
            pokemon=pokemon_map,
            round=round_number,
            phase=phase,
            rng=rng,
        )
        dispatcher = TrainerFeatureDispatcher(battle)
        feature = {"feature_id": "context-probe", "conditions": conditions}
        result = dispatcher._feature_matches_context(
            trainer_id="trainer-a",
            trainer=trainer_a,
            feature=feature,
            actor_id=actor_id,
            payload=dict(payload or {}),
        )
        next_roll = rng.random()
        return name, int(bool(result)), next_roll.hex()

    cases = [
        evaluate("baseline", {}),
        evaluate("non_dict_conditions", "anything"),
        evaluate("actor_required_missing", {"actor_required": True}, actor_id=None),
        evaluate("self_scope_pass", {"actor_scope": "self_team"}),
        evaluate("self_scope_fail", {"actor_scope": "ally"}, actor_id="mon-b"),
        evaluate("enemy_scope_pass", {"actor_scope": "enemy"}, actor_id="mon-b"),
        evaluate("enemy_scope_missing_fail", {"actor_scope": "foe"}, actor_id="missing"),
        evaluate("trainer_scope_pass", {"actor_scope": "trainer"}, actor_id="trainer-a"),
        evaluate("trainer_scope_fail", {"actor_scope": "trainer"}),
        evaluate("pokemon_scope_pass", {"actor_scope": "pokemon"}),
        evaluate("pokemon_scope_fail", {"actor_scope": "pokemon"}, actor_id="trainer-a"),
        evaluate("phase_payload_pass", {"phase_in": ["start", "action"]}, payload={"phase": " ACTION "}),
        evaluate("phase_battle_fallback_pass", {"phase": "action"}),
        evaluate("phase_fail", {"phase": "end"}),
        evaluate("action_type_pass", {"action_types": ["shift", "standard"]}, payload={"action_type": "STANDARD"}),
        evaluate("action_type_fail", {"action_type": "free"}, payload={"action_type": "standard"}),
        evaluate("move_name_pass", {"move_names": ["tackle", "ember"]}, payload={"move_name": "EMBER"}),
        evaluate("move_category_pass", {"move_category": "physical"}, payload={"move_category": "PHYSICAL"}),
        evaluate("actor_active_pass", {"actor_active": "yes"}),
        evaluate("actor_active_fail", {"actor_active": True}, actor_id="mon-a-inactive"),
        evaluate("actor_active_missing_fail", {"actor_active": False}, actor_id="trainer-a"),
        evaluate("min_round_pass", {"min_round": 3}),
        evaluate("min_round_fail", {"min_round": 4}),
        evaluate("max_round_pass", {"max_round": 3}),
        evaluate("max_round_fail", {"max_round": 2}),
        evaluate("damage_fallback_pass", {"min_damage": 6}, payload={"damage": "bad", "damage_dealt": 7}),
        evaluate("damage_direct_precedence_fail", {"min_damage": 6}, payload={"damage": 2, "damage_dealt": 99}),
        evaluate("max_damage_fail", {"max_damage": 5}, payload={"total_damage": 6}),
        evaluate("once_actor_unused_pass", {"once_per_actor_per_round": True}),
        evaluate(
            "once_actor_used_fail",
            {"once_per_actor_per_round": True},
            usage={"context-probe": {"actor_round_mon-a_3": 1}},
        ),
        evaluate("chance_zero_no_rng", {"chance": 0}),
        evaluate("chance_percent", {"chance": 25}, seed=7),
        evaluate("chance_fraction", {"chance": 0.75}, seed=7),
        evaluate("chance_clamped_one", {"chance": 250}, seed=7),
        evaluate("guard_before_chance_no_rng", {"min_round": 99, "chance": 1.0}, seed=7),
    ]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for name, result, next_roll in cases:
            handle.write(f"{name}\t{result}\t{next_roll}\n")


if __name__ == "__main__":
    main()
