#!/usr/bin/env python3
"""Export observed tile-entry terrain-trap outcomes from the pinned Python oracle."""
from __future__ import annotations

import argparse
import ast
import re
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
    # Scenario labels are canonical terrain names; this shim only provides the global
    # required by the extracted oracle method without importing the complete battle engine.
    return re.sub(r"[^a-z0-9]+", " ", str(value or "").strip().lower()).strip()


class FakeActor:
    def __init__(self, naturewalk: list[str]) -> None:
        self.position = (4, 3)
        self.hp = 37
        self.fainted = False
        self._naturewalk = list(naturewalk)

    def naturewalk_labels(self) -> list[str]:
        return list(self._naturewalk)


class FakeState:
    def __init__(self, layers: int, source_team: str, terrains: list[str], naturewalk: list[str]) -> None:
        self.actor = FakeActor(naturewalk)
        self.pokemon = {"target": self.actor}
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
        self.teams = {"target": "blue", "source": source_team}

    def _team_for(self, actor_id: str) -> str:
        return self.teams.get(actor_id, "")

    def log_event(self, event: dict) -> None:
        self.events.append(dict(event))

    def _consume_trap(self, coord: tuple[int, int], trap_key: str) -> None:
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
    namespace = {"_normalize_terrain_name": normalize_terrain_name}
    exec(compile(module, str(source), "exec"), namespace)
    return namespace[FUNCTION]


def encode(values: list[str]) -> str:
    return "|".join(str(value) for value in values)


def observed_row(method, scenario: str, layers: int, source_team: str, terrains: list[str], naturewalk: list[str]) -> list[str]:
    state = FakeState(layers, source_team, terrains, naturewalk)
    method(state, "target")
    event = state.events[0] if state.events else {}
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
    ]
    text = "\t".join(header) + "\n" + "\n".join("\t".join(row) for row in rows) + "\n"
    args.output.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
