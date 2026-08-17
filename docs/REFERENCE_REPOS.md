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

## 2. DeepSoftwareAnalytics/RepoTransBench

Repository: https://github.com/DeepSoftwareAnalytics/RepoTransBench
License: MIT
Relevance: Extremely high for migration process

Why it matters:
- It targets repository-level code translation rather than isolated snippets.
- Python -> Java is an explicit supported translation direction.
- Its RepoTransAgent repeatedly reads source, creates target files, executes builds/tests, searches dependencies, and iterates on errors.
- It evaluates compilation separately from functional test success.
- Its published benchmark results show why dynamic Python -> static Java cannot be treated as a one-shot transpilation problem.

What AutoPTU should borrow directly:
- A repeatable per-subsystem translation loop instead of ad-hoc prompting.
- Separate `COMPILES`, `JAVA_TESTS_PASS`, and `PYTHON_PARITY_PASS` gates.
- Feed source structure, source tests, target tests, and build commands into the migration task.
- Iterate from concrete compiler/test failures instead of translating more code when a slice is already broken.

AutoPTU goes one step further: the Python implementation is still available as an executable oracle, so every translated slice can be differentially tested.

See `MIGRATION_AGENT_PROTOCOL.md`.

## 3. MegaMek/megamek

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

## 4. GAIGResearch/TabletopGames (TAG)

Repository: https://github.com/GAIGResearch/TabletopGames
License: MIT
Relevance: Very high for the Java core architecture

Why it matters:
- A Java 21 framework specifically for modern tabletop/board games.
- Its core API separates `GameState`, a forward model, legal-action calculation, and action application.
- The forward model is intentionally stateless and exposes a controlled API for AI players.
- It already supports multiple games and JSON-defined data, which makes it useful for understanding how to keep reusable engine infrastructure separate from content.

What AutoPTU should borrow:
- `BattleState` should become typed state plus controllers/resolvers rather than remaining the owner of every rule.
- Legal action generation should be a first-class API.
- Applying an action should be a deterministic forward-model operation.
- AI should consume the same legal-action/state API as other controllers instead of bypassing the rules engine.

This is one of the strongest references for the shape of the eventual AutoPTU Java API.

## 5. triplea-game/triplea

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

## 6. FreeCol/freecol

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

## 7. marcrd/Spellsource-Server / hiddenswitch/Spellsource

Repositories:
- https://github.com/marcrd/Spellsource-Server
- https://github.com/hiddenswitch/Spellsource

Relevance: High for rules-heavy Java/Python coexistence

Why it matters:
- A Java card-battle engine/server with large numbers of individual card mechanics.
- The older server exposed the Java engine to Python with a near 1-to-1 API bridge for simulation and AI work.
- The newer project keeps a substantial Java backend while also using separate web, Python, and Unity components.

What AutoPTU should borrow:
- During migration, Python should be able to drive/observe Java battles without Minecraft.
- Keep the Java engine API narrow enough to drive from a parity harness.
- Large numbers of bespoke rules belong behind structured rule/effect boundaries, not UI code.

## 8. chrishumphreys/p2j

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

This idea is already implemented in `tools/python/export_runtime_types.py`.

## 9. facebookresearch/CodeGen / TransCoder and CoTran

Repositories:
- https://github.com/facebookresearch/CodeGen
- https://github.com/facebookresearch/TransCoder
- https://github.com/PrithwishJana/CoTran

Relevance: Medium

Why they matter:
- These projects study Java/Python translation at function/program granularity.
- TransCoder-ST and CoTran use execution/compiler/test feedback instead of relying only on textual similarity.

What AutoPTU should borrow:
- Translation assistance for bounded pure functions after contracts and fixtures exist.
- Compiler/test feedback as an iterative repair signal.

Caution:
- The standalone TransCoder repository is archived.
- These are not substitutes for repository architecture work.
- Generated translations are drafts until AutoPTU Python parity passes.

## 10. GAIGResearch/Tribes + ClaireBookworm/polytopia_rl

Repositories:
- https://github.com/GAIGResearch/Tribes
- https://github.com/ClaireBookworm/polytopia_rl

Relevance: High for the AI/runtime boundary

Why they matter:
- Tribes is a Java implementation/forward model of a turn-based strategy game for AI research.
- `polytopia_rl` wraps that Java rules engine with a lightweight Python bridge and Gym-style `reset/step/list_actions/observation` APIs.
- The Java engine remains authoritative while Python is used for rapid AI experimentation.

What AutoPTU should borrow:
- Keep Java as the production rules runtime.
- Keep a Python-accessible simulation boundary for AI research, regression tooling, and comparison while useful.
- Model legal actions and observations explicitly rather than letting AI mutate battle internals.

This is especially relevant because AutoPTU eventually needs AI-vs-AI battles but does not need Python to remain in the Minecraft deployment.

## 11. battlecode/battlecode26

Repository: https://github.com/battlecode/battlecode26
Relevance: High for engine/protocol separation

Why it matters:
- Separates the JVM game engine, specifications/schema, clients/visualization, and bot-facing interfaces.
- Demonstrates how a deterministic battle runtime can be headless while a separate viewer consumes structured state/results.

What AutoPTU should borrow:
- A language-neutral battle/event schema between core and renderer.
- Headless battle execution as a first-class product capability.
- Keep Minecraft rendering downstream from engine state rather than entangled with rules.

## 12. robocode-dev/tank-royale

Repository: https://github.com/robocode-dev/tank-royale
Relevance: Medium-high for headless simulation and multi-language control

Why it matters:
- The runtime is JVM-based and exposes battle execution independently from the viewer.
- It supports bot APIs in Python, Java/JVM, .NET, and TypeScript/JavaScript through a protocol boundary.
- It has a Battle Runner API for headless programmatic battles.

What AutoPTU should borrow:
- The battle engine should run headlessly without Minecraft.
- AI/control clients should communicate through stable battle/action contracts.
- Viewer/runtime separation makes regression testing and tournament simulation easier.

## 13. jpy-consortium/jpy

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
2. Use RepoTransBench's repository-level iteration philosophy: inspect, translate a bounded slice, compile, test, repair.
3. Add AutoPTU's stronger acceptance gates: `COMPILES`, `JAVA_TESTS_PASS`, and `PYTHON_PARITY_PASS`.
4. Use Python runtime type tracing to make Java type design less guessy.
5. Build golden fixtures from Python for every ported subsystem.
6. Run Python and Java differential tests in CI against a pinned Python oracle commit.
7. Make RNG consumption and ordered event output part of the compatibility contract.
8. Use translation models/tools only to produce drafts of isolated functions/classes.
9. Study TAG, MegaMek, TripleA, and FreeCol for game-state/forward-model/phase decomposition, without importing incompatible licensed implementation code.
10. Keep Java battle simulation independently runnable, following the same broad engine-vs-client separation visible in Battlecode, Robocode, Tribes, and Spellsource.
11. Keep Minecraft/Craftics outside the rules tests. They should adapt to the Java battle core only after rules parity is established.
