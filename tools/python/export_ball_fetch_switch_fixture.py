#!/usr/bin/env python3
"""Freeze Ball Fetch behavior from the pinned Python switch runtime."""
from __future__ import annotations

import argparse
import inspect
import random
import sys
import textwrap
from pathlib import Path


class SequenceRNG(random.Random):
    def __init__(self, values):
        super().__init__()
        self._values = list(values)

    def randint(self, a, b):
        if self._values:
            return self._values.pop(0)
        return b


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(Path(args.source_root).resolve()))
    from auto_ptu.data_models import MoveSpec, PokemonSpec
    from auto_ptu.rules import BattleState, GridState, PokemonState, TrainerState

    source = textwrap.dedent(inspect.getsource(BattleState._trigger_ball_fetch))

    move = MoveSpec(
        name="Tackle",
        type="Normal",
        category="Physical",
        db=6,
        ac=2,
        range_kind="Melee",
        range_text="Melee, 1 Target",
    )

    def spec(name: str, *, ability: str | None = None, overland: int = 4) -> PokemonSpec:
        return PokemonSpec(
            species=name,
            level=20,
            types=["Normal"],
            hp_stat=10,
            atk=12,
            defense=10,
            spatk=12,
            spdef=10,
            spd=10,
            moves=[move],
            abilities=[{"name": ability}] if ability else [],
            movement={"overland": overland},
        )

    trainer_a = TrainerState(identifier="a", name="A", team="players")
    trainer_b = TrainerState(identifier="b", name="B", team="foes")
    outgoing = PokemonState(spec=spec("Outgoing"), controller_id="a", position=(2, 2), active=True)
    replacement = PokemonState(spec=spec("Replacement"), controller_id="a", position=None, active=False)
    fetcher = PokemonState(spec=spec("Fetcher", ability="Ball Fetch", overland=4), controller_id="a", position=(7, 2), active=True)
    enemy_fetcher = PokemonState(spec=spec("EnemyFetcher", ability="Ball Fetch", overland=4), controller_id="b", position=(7, 7), active=True)

    battle = BattleState(
        trainers={"a": trainer_a, "b": trainer_b},
        pokemon={"a-1": outgoing, "a-2": replacement, "a-3": fetcher, "b-1": enemy_fetcher},
        grid=GridState(width=10, height=10),
    )
    battle.rng = SequenceRNG([20] * 200)
    battle.round = 1

    before_fetcher = fetcher.position
    before_enemy = enemy_fetcher.position
    before_log_len = len(battle.log)
    battle._apply_switch(
        outgoing_id="a-1",
        replacement_id="a-2",
        initiator_id="a",
        allow_replacement_turn=False,
        allow_immediate=False,
    )
    new_events = battle.log[before_log_len:]
    ball_fetch_events = [event for event in new_events if event.get("ability") == "Ball Fetch"]

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        handle.write("SOURCE_BEGIN\n")
        for index, line in enumerate(source.splitlines(), start=1):
            handle.write(f"SOURCE\t{index}\t{line}\n")
        handle.write("SOURCE_END\n")
        handle.write(f"REPLACEMENT_POSITION\t{replacement.position[0]}\t{replacement.position[1]}\n")
        handle.write(f"FETCHER_BEFORE\t{before_fetcher[0]}\t{before_fetcher[1]}\n")
        handle.write(f"FETCHER_AFTER\t{fetcher.position[0]}\t{fetcher.position[1]}\n")
        handle.write(f"ENEMY_BEFORE\t{before_enemy[0]}\t{before_enemy[1]}\n")
        handle.write(f"ENEMY_AFTER\t{enemy_fetcher.position[0]}\t{enemy_fetcher.position[1]}\n")
        handle.write(f"BALL_FETCH_EVENT_COUNT\t{len(ball_fetch_events)}\n")
        for index, event in enumerate(ball_fetch_events):
            payload = repr(sorted(event.items()))
            handle.write(f"BALL_FETCH_EVENT\t{index}\t{payload}\n")
            origin = event["from"]
            destination = event["to"]
            handle.write(
                "BALL_FETCH_EVENT_STRUCT\t"
                f"{index}\t{event['actor']}\t{event['target']}\t{event['ability']}\t{event['effect']}\t"
                f"{origin[0]}\t{origin[1]}\t{destination[0]}\t{destination[1]}\t"
                f"{event['description']}\t{event['target_hp']}\t{event['phase']}\t{event['round']}\n"
            )
    print(output)


if __name__ == "__main__":
    main()
