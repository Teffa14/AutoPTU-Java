# AutoPTU Java Core

Clean Java 21 port of the AutoPTU battle rules engine.

This repository is intentionally **not** a Minecraft mod yet. The first goal is behavioral parity with the existing Python AutoPTU engine. Craftics, Cobblemon, and Minecraft should consume this library later instead of owning PTU rules themselves.

The target runtime is an AI-driven tactical grid autobattler inside Minecraft: AutoPTU-Java decides legal actions and battle results; Minecraft/Cobblemon/Craftics adapt world state and render the resulting events.

## Source oracle

The Python implementation in `Teffa14/AutoPTU` remains authoritative while this port is incomplete.

The migration uses deterministic fixtures:

```text
Same battle input + same RNG stream
            |
      +-----+-----+
      |           |
 Python AutoPTU  Java AutoPTU
      |           |
  ordered events / final state
      +-----compare-----+
```

Java passes only when normalized behavior matches the Python oracle.

## Port rule

Do not translate the Python monolith line by line.

For each subsystem:

1. Define a language-neutral input/output contract.
2. Freeze Python fixtures for representative scenarios.
3. Implement the same behavior in Java.
4. Compare ordered events and final state.
5. Move to the next subsystem only after parity tests pass.

## Current status

- [x] Java 21 library skeleton.
- [x] Cross-language oracle input/output contracts.
- [x] Stable-value canonicalizer matching Python oracle normalization.
- [x] Python `random.Random` integer-seed compatibility for the RNG operations currently required by the port.
- [x] Targeting: range, areas, footprints, target anchors, and line of sight.
- [x] Shift movement legality: Overland/Swim/Sky, terrain costs, blockers, Wallrunner, sprint, and landing-fit boundary.
- [x] Jump movement: long jump, high jump, blocked-path behavior, Wallrunner extension, water landing rules, and fit predicates.
- [x] Core PTU tables: Damage Base dice table and type-effectiveness step chart.
- [x] Calculation primitives: stages, accuracy stages, weather DB, crit probability, Burn, flat/scalar modifiers, and rounding points.
- [x] Invariant d20 accuracy resolution: needed roll, natural 1/20, crit threshold, Blur, melee No Guard, and Probability Control rerolls.
- [x] Combat stat resolution: offensive, defensive, speed, combat-stage/status interactions, and resolved modifiers.
- [x] Typed turn flow: ActionType, TurnPhase, phase sequence, and action budget.
- [x] Deterministic initiative ordering, Trick Room ordering, League ordering, and declared-action ordering.
- [x] Deterministic autobattler action-space contract: Shift, direct combatant targets, SELF/FIELD, tile-aimed AoE, footprints, LoS, and action-budget filtering.
- [x] Python runtime type-manifest exporter for designing Java records/interfaces from observed engine behavior.
- [x] Cross-repository CI that checks out a pinned Python AutoPTU commit and compares ported rule slices against it.
- [x] Reference-repository research for Python->Java migration and large tabletop/game-engine architecture.
- [x] Defined migration acceptance protocol with separate compile, Java-test, and Python-parity gates.
- [x] Defined Minecraft autobattler architecture and core/adapter boundary.
- [ ] Expand RNG compatibility only as new Python call sites require it.
- [ ] Port core combatant/grid battle state.
- [ ] Port full damage resolution pipeline and remaining stateful accuracy modifiers.
- [ ] Port status controller, terrain, hazards, forced movement, and reactions.
- [ ] Port move, ability, item, perk, and Trainer Feature hook registries.
- [ ] Port semantic battle-event emission and full BattleSpec -> BattleTranscript parity.
- [ ] Port AI scoring/policy over the legal `BattleChoice` list.
- [ ] Add Craftics/Cobblemon adapter after a parity-safe vertical slice exists.

See `docs/REFERENCE_REPOS.md` for migration research, `docs/MIGRATION_AGENT_PROTOCOL.md` for the acceptance loop, `docs/MINECRAFT_AUTOBATTLER_ARCHITECTURE.md` for the Minecraft runtime boundary, `docs/TYPE_TRACING.md` for runtime type discovery, and `docs/PORTING_PLAN.md` for the ordered port plan.

## Build

```bash
gradle test
```

GitHub Actions validates the Java core and Python-oracle parity on every push and pull request.
