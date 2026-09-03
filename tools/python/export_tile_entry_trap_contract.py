#!/usr/bin/env python3
"""Export observed tile-entry terrain-trap outcomes from the pinned Python oracle."""
from __future__ import annotations

import argparse
import ast
import re
from dataclasses import dataclass
from pathlib import Path
from types import SimpleNamespace

SOURCE = Path("auto_ptu/rules/battle_state.py")
FUNCTION = "_trigger_tile_traps_on_entry"


def find_function(tree: ast.AST) -> ast.FunctionDef:
    matches = [node for node in ast.walk(tree) if isinstance(node, ast.FunctionDef) and node.name == FUNCTION]
    if len(matches) != 1:
        raise SystemExit(f"expected exactly one {FUNCTION}, found {len(matches)}")
    return matches[0]


def normalize_terrain_name(value: object) -> str:
    return re.sub(r"[^a-z0-9]+", " ", str(value or "").strip().lower()).strip()


@dataclass
class FakeMoveSpec:
    name: str
    type: str
    category: str


class FakeActor:
    def __init__(self, naturewalk: list[str]) -> None:
        self.name = "target"
        self.position = (4, 3)
        self.hp = 37
        self.fainted = False
        self.combat_stages: dict[str, int] = {}
        self._naturewalk = list(naturewalk)

    def naturewalk_labels(self) -> list[str]:
        return list(self._naturewalk)


class FakeState:
    def __init__(self, layers: int, source_team: str, terrains: list[str], naturewalk: list[str]) -> None:
        self.actor = FakeActor(naturewalk)
        self.pokemon = {"target": self.actor}
        self.round = 7
        self.grid = SimpleNamespace(
            tiles={
                self.actor.position: {
                    "traps": {"abrasion_trap": layers},
                    "trap_sources": {
                        "abrasion_trap": {
                            "source_id": "source",
                            "terrains": list(terrains),
                            "trap_name": "Abrasion Trap",
                        }
                    },
                }
            }
        )
        self.events: list[dict] = []
        self.status_applications: list[dict] = []
        self.trace: list[str] = []
        self.teams = {"target": "blue", "source": source_team}

    def _team_for(self, actor_id: str) -> str:
        return self.teams.get(actor_id, "")

    def log_event(self, event: dict) -> None:
        self.events.append(dict(event))
        self.trace.append("EMIT_TRAP_EVENT")

    def _apply_status(
        self,
        _events: list,
        *,
        attacker_id: str,
        target_id: str,
        move: FakeMoveSpec,
        target: FakeActor,
        status: str,
        effect: str,
        description: str,
        remaining: int,
    ) -> None:
        self.status_applications.append({
            "actor": attacker_id,
            "target": target_id,
            "target_name": target.name,
            "status": status,
            "move_name": move.name,
            "move_type": move.type,
            "move_category": move.category,
            "effect": effect,
            "description": description,
            "remaining": remaining,
        })
        self.trace.append("APPLY_STATUS")

    def _consume_trap(self, coord: tuple[int, int], trap_key: str) -> None:
        self.trace.append("CONSUME_TRAP")
        tile = self.grid.tiles.get(coord, {})
        traps = dict(tile.get("traps") or {})
        traps.pop(trap_key, None)
        tile["traps"] = traps
        sources = dict(tile.get("trap_sources") or {})
        sources.pop(trap_key, None)
        tile["trap_sources"] = sources
        self.grid.tiles[coord] = tile

    def _tile_frozen_domain_entries(self, _coord: tuple[int, int]) -> list[dict]:
        return []


def load_oracle_method(source: Path):
    tree = ast.parse(source.read_text(encoding="utf-8-sig"), filename=str(source))
    fn = find_function(tree)
    module = ast.fix_missing_locations(ast.Module(body=[fn], type_ignores=[]))
    namespace = {
        "_normalize_terrain_name": normalize_terrain_name,
        "MoveSpec": FakeMoveSpec,
    }
    exec(compile(module, str(source), "exec"), namespace)
    return namespace[FUNCTION]


def encode(values: list[str]) -> str:
    return "|".join(str(value) for value in values)


def observed_row(method, scenario: str, layers: int, source_team: str, terrains: list[str], naturewalk: list[str]) -> list[str]:
    state = FakeState(layers, source_team, terrains, naturewalk)
    method(state, "target")
    event = state.events[0] if state.events else {}
    status = state.status_applications[0] if state.status_applications else {}
    remaining = state.grid.tiles[state.actor.position].get("traps") or {}
    consumed = "1" if "abrasion_trap" not in remaining else "0"
    coord = event.get("coord") or []
    return [
        scenario,
        str(layers),
        source_team,
        encode(terrains),
        encode(naturewalk),
        str(event.get("effect") or ""),
        consumed,
        str(event.get("source_id") or ""),
        encode([str(value) for value in (event.get("terrains") or [])]),
        encode([str(value) for value in coord]),
        str(event.get("target_hp") if event.get("target_hp") is not None else ""),
        str(event.get("trap_name") or ""),
        str(event.get("description") or ""),
        str(status.get("status") or ""),
        str(status.get("actor") or ""),
        str(status.get("target") or ""),
        str(status.get("move_name") or ""),
        str(status.get("move_type") or ""),
        str(status.get("move_category") or ""),
        str(status.get("effect") or ""),
        str(status.get("description") or ""),
        str(status.get("remaining") if status.get("remaining") is not None else ""),
        encode(state.trace),
        str(event.get("actor") or ""),
        encode(sorted(str(key) for key in event.keys())),
    ]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    method = load_oracle_method(args.source_root / SOURCE)
    rows = [
        observed_row(method, "negative_layers", -2, "red", ["mountain"], []),
        observed_row(method, "zero_layers", 0, "red", ["mountain"], []),
        observed_row(method, "same_team_source", 1, "blue", ["mountain"], []),
        observed_row(method, "naturewalk_match", 1, "red", ["forest", "wetlands"], ["forest"]),
        observed_row(method, "enemy_entry", 3, "red", ["mountain", "cave"], []),
    ]

    args.output.parent.mkdir(parents=True, exist_ok=True)
    header = [
        "scenario", "layers", "source_team", "terrains", "naturewalk", "effect", "consumed",
        "source_id", "event_terrains", "coord", "target_hp", "trap_name", "description",
        "status", "status_actor", "status_target", "status_move_name", "status_move_type",
        "status_move_category", "status_effect", "status_description", "status_remaining", "trace",
        "event_actor", "event_keys",
    ]
    text = "\t".join(header) + "\n" + "\n".join("\t".join(row) for row in rows) + "\n"
    args.output.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
