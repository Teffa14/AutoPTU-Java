# AutoPTU Java Core

Clean Java 21 port of the AutoPTU battle rules engine.

This repository is intentionally **not** a Minecraft mod yet. The first goal is behavioral parity with the existing Python AutoPTU engine. Craftics, Cobblemon, and Minecraft should consume this library later instead of owning PTU rules themselves.

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
- [x] Cross-language oracle contract types.
- [x] First real rules port: targeting, range, areas, footprints, and line of sight.
- [x] Java tests for the first targeting slice.
- [ ] Export golden targeting fixtures from Python.
- [ ] Port deterministic RNG contract.
- [ ] Port core calculations.
- [ ] Port movement legality.
- [ ] Port action economy and phases.
- [ ] Port statuses and effects.
- [ ] Port move, ability, item, perk, and Trainer Feature hook registries.
- [ ] Port AI policy after rules parity.
- [ ] Add Craftics/Cobblemon adapter after the core is stable.

## Build

```bash
gradle test
```

GitHub Actions validates the Java core on every push and pull request.
