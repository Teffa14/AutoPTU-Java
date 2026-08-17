# Reference Repositories for the AutoPTU Python -> Java Port

These projects are references, not dependencies by default. The goal is to borrow architecture and migration techniques without importing incompatible code or coupling AutoPTU Core to another engine.

## 1. pkmn/engine

Repository: https://github.com/pkmn/engine
License: MIT
Relevance: Very high

Why it matters:
- It is a low-level Pokemon battle engine built around compatibility with an existing battle implementation.
- It separates legal choices from battle updates.
- It has explicit protocol/log output for observing state transitions.
- Its testing methodology emphasizes compatibility instead of source-code similarity.
- It includes deterministic/fixed RNG facilities for reproducible battle behavior.

What AutoPTU should borrow:
- Treat Python AutoPTU as executable behavior specification.
- Expose `legalChoices(state)` and `applyChoice(state, choice)` style boundaries where possible.
- Keep ordered battle events as part of correctness, not just final HP.
- Make RNG injectable and fixture-driven.
- Turn every discovered differential mismatch into a permanent regression case.

Do not copy its Zig implementation. The useful part is the compatibility methodology and protocol design.

## 2. MegaMek/megamek

Repository: https://github.com/MegaMek/megamek
License: GPL-3.0
Relevance: Very high as an architectural reference

Why it matters:
- MegaMek is a large Java implementation of the BattleTech tabletop rules.
- It handles movement, weapons, physical attacks, terrain/maps, initiative/phases, custom units, scenarios, and network multiplayer.
- This is much closer to AutoPTU's real complexity than a small Pokemon battle simulator.
- Its game flow uses explicit typed phases rather than treating the entire match as one resolver.

What AutoPTU should borrow conceptually:
- Explicit game phases instead of one giant resolver.
- Strong typed model objects for units, board state, attacks, and phase state.
- Separate rules/calculation services from presentation/network code.
- Scenario-driven testing for complicated tabletop interactions.

License warning:
- GPL-3.0. Study architecture and public interfaces, but do not copy implementation code into AutoPTU-Java unless we intentionally accept GPL obligations.

## 3. triplea-game/triplea

Repository: https://github.com/triplea-game/triplea
License: GPL-3.0
Relevance: High

Why it matters:
- A mature Java turn-based strategy and board-game engine.
- Supports AI, multiplayer/lobby play, user-created maps, scenarios, and rule variations.
- Useful for seeing how a Java project keeps game data/scenarios separate from reusable engine infrastructure.

What AutoPTU should borrow conceptually:
- Data-driven scenario/content boundaries.
- Turn/phase orchestration patterns.
- Separation between engine, game data, AI, and network/UI layers.

License warning:
- GPL-3.0. Architecture reference only unless licensing is intentionally changed.

## 4. FreeCol/freecol

Repository: https://github.com/FreeCol/freecol
License: GPL-2.0
Relevance: High as a migration philosophy reference

Why it matters:
- FreeCol is a Java turn-based strategy game built to reproduce the gameplay and rules of an existing game.
- Its project description explicitly emphasizes an incremental clone: add features one at a time while keeping a running program throughout development.
- It modernizes presentation and adds multiplayer without making visual implementation responsible for the original rules.

What AutoPTU should borrow conceptually:
- Preserve behavior while allowing the implementation and presentation layer to change completely.
- Keep the Java port executable throughout migration instead of waiting for an all-at-once rewrite.
- Port one vertical rule slice at a time and retain a usable engine at every milestone.

License warning:
- GPL-2.0. Architecture/process reference only unless licensing is intentionally changed.

## 5. marcrd/Spellsource-Server

Repository: https://github.com/marcrd/Spellsource-Server
Relevance: High for migration tooling

Why it matters:
- A Java card-battle engine/server with large numbers of individual card mechanics.
- It exposes the Java engine to Python with a near 1-to-1 API bridge for simulation and AI work.
- This demonstrates that a rules-heavy Java engine can remain testable/researchable from Python while the runtime itself is Java.

What AutoPTU should borrow:
- During migration, Python should be able to invoke Java battles and compare outputs automatically.
- Keep the Java engine API narrow enough to drive from a parity harness.
- Avoid making Minecraft/Craftics the test harness. The battle core should be runnable independently.

## 6. chrishumphreys/p2j

Repository: https://github.com/chrishumphreys/p2j
License: GPL-3.0
Relevance: High for one specific technique, low as a production transpiler

Why it matters:
- It was written to help convert a Python game to Java/Android.
- It combines AST/source translation with runtime profiling to infer Python argument types.
- Its own documentation says generated code still requires substantial manual cleanup.

What AutoPTU should borrow:
- Build our own AutoPTU runtime type-manifest exporter.
- Exercise Python battle tests/scenarios and record the concrete types that cross subsystem boundaries.
- Use that manifest when designing Java records/interfaces.

What not to do:
- Do not run the entire AutoPTU monolith through p2j and trust the output.
- Do not copy GPL translator code into this repository.

## 7. facebookresearch/CodeGen / TransCoder-ST

Repositories:
- https://github.com/facebookresearch/CodeGen
- https://github.com/facebookresearch/TransCoder

Relevance: Medium

Why it matters:
- TransCoder supports Python/Java translation.
- TransCoder-ST specifically uses automated tests to improve/validate code translation.
- This independently validates the strategy we are using: translation is useful only when backed by behavioral tests.

What AutoPTU should borrow:
- Translation at function/module granularity, not whole-repository conversion.
- Unit tests and differential execution as the acceptance criterion.

Caution:
- The standalone TransCoder repository is archived.
- Generated translations should be treated as drafts only.

## 8. jpy-consortium/jpy

Repository: https://github.com/jpy-consortium/jpy
Relevance: Medium as a temporary bridge

Why it matters:
- Maintained bi-directional Python/Java bridge.
- Can embed Java in Python or Python in Java.
- Designed for efficient cross-language calls.

Possible AutoPTU use:
- Differential tests could invoke Java methods directly from Python while the port is incomplete.
- This may be useful if file/subprocess fixtures become too slow or awkward.

Do not make it a production requirement for the Minecraft mod. The target remains a standalone Java battle core.

## Rejected as primary solutions

### BeeWare VOC

VOC compiles/transpiles Python for JVM execution, but that solves a different problem: running Python semantics on the JVM. AutoPTU needs maintainable Java source and a Java-native battle core that Craftics/Cobblemon can consume directly. VOC is also archived, so it is not a foundation for this migration.

### Wholesale source-to-source transpilation

A generated Java clone of the 40k-line BattleState would preserve the same coupling while introducing translation uncertainty. It would make debugging parity harder, not easier. Translation tools are useful only for isolated functions after their contracts and fixtures exist.

## Recommended strategy after this research

1. Keep manual/assisted Java ports as the source of production code.
2. Use Python runtime type tracing to make Java type design less guessy.
3. Build golden fixtures from Python for every ported subsystem.
4. Run Python and Java differential tests in CI against a pinned Python oracle commit.
5. Make RNG consumption and ordered event output part of the compatibility contract.
6. Use translation tools only to produce drafts of isolated functions.
7. Require Java tests plus Python-vs-Java differential tests before declaring a subsystem ported.
8. Study MegaMek, TripleA, and FreeCol for decomposition patterns, but keep GPL implementation code out of this clean-room port unless licensing is deliberately changed.
9. Keep Minecraft/Craftics outside the rules tests. They should consume the Java battle core only after rules parity is established.
