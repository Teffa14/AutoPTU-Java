#!/usr/bin/env python3
"""Freeze generic combat-stage hook dispatch and Simple reaction behavior."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from types import SimpleNamespace


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    source_root = args.source_root.resolve()
    sys.path.insert(0, str(source_root))

    hooks_path = source_root / "auto_ptu" / "rules" / "hooks" / "combat_stage_hooks.py"
    hooks_source = hooks_path.read_text(encoding="utf-8")
    reactions_path = source_root / "auto_ptu" / "rules" / "hooks" / "abilities" / "combat_stage_reactions.py"
    reactions_source = reactions_path.read_text(encoding="utf-8")

    contracts = {
        "registry_is_phase_scoped": int('_COMBAT_STAGE_HOOKS.get(phase, [])' in hooks_source),
        "registration_preserves_order": int('_COMBAT_STAGE_HOOKS.setdefault(phase, []).append(func)' in hooks_source),
        "dispatch_preserves_order": int('for func in hooks:' in hooks_source),
        "dispatch_returns_new_events_only": int('return ctx.events[start:]' in hooks_source),
        "simple_is_post_apply": int('@register_combat_stage_hook("post_apply")' in reactions_source),
        "simple_requires_nonzero_applied_delta": int('if ctx.applied_delta == 0:' in reactions_source),
        "simple_reads_target_ability": int('ctx.target.has_ability("Simple")' in reactions_source),
        "simple_reapplies_applied_delta": int('current + ctx.applied_delta' in reactions_source),
        "simple_clamps_stage": int('max(-6, min(6, current + ctx.applied_delta))' in reactions_source),
        "simple_emits_ability_event": int('"ability": "Simple"' in reactions_source and '"effect": "simple"' in reactions_source),
    }
    if not all(contracts.values()):
        raise RuntimeError(f"pinned oracle combat-stage hook contract changed: {contracts}")

    from auto_ptu.rules.hooks.abilities.combat_stage_reactions import _simple_doubles_stage_changes

    class Target:
        def __init__(self, stage: int, has_simple: bool) -> None:
            self.combat_stages = {"atk": stage}
            self.hp = 20
            self._has_simple = has_simple

        def has_ability(self, name: str) -> bool:
            return self._has_simple and name == "Simple"

    scenarios = [
        ("simple_raise", 1, 1, True),
        ("simple_drop", -1, -1, True),
        ("simple_raise_two", 1, 2, True),
        ("simple_upper_clamp", 6, 1, True),
        ("simple_lower_clamp", -6, -1, True),
        ("zero_delta", 0, 0, True),
        ("no_simple", 1, 1, False),
    ]

    rows = []
    for scenario, start_stage, applied_delta, has_simple in scenarios:
        target = Target(start_stage, has_simple)
        events: list[dict] = []
        ctx = SimpleNamespace(
            events=events,
            target=target,
            stat="atk",
            applied_delta=applied_delta,
            target_id="target",
            move=SimpleNamespace(name="Test Move"),
        )
        _simple_doubles_stage_changes(ctx)
        event = events[0] if events else {}
        rows.append((
            scenario,
            start_stage,
            applied_delta,
            int(has_simple),
            int(target.combat_stages["atk"]),
            len(events),
            str(event.get("effect") or ""),
            int(event.get("amount") or 0),
        ))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = ["scenario\tstart_stage\tapplied_delta\thas_simple\texpected_stage\tevent_count\tevent_effect\tevent_amount\n"]
    lines.extend("\t".join(map(str, row)) + "\n" for row in rows)
    args.output.write_text("".join(lines), encoding="utf-8")
    print(f"wrote {len(rows)} Python combat-stage hook fixtures to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
