# AutoPTU Python -> Java Migration Agent Protocol

This document defines the acceptance loop for every AutoPTU subsystem port. It is inspired by repository-level translation systems such as RepoTransBench/RepoTransAgent, but it adds a stronger requirement: the existing Python AutoPTU engine is an executable oracle, so compilation and ordinary Java tests are not enough.

## Completion states

Every port slice is tracked through three independent gates:

1. `COMPILES` - the Java implementation and its tests compile.
2. `JAVA_TESTS_PASS` - targeted Java unit/regression tests pass.
3. `PYTHON_PARITY_PASS` - Java output matches the pinned Python oracle for the same scenarios.

A subsystem is not considered ported until all three are true.

For stateful battle slices, a fourth gate is required:

4. `TRACE_PARITY_PASS` - ordered semantic events, RNG consumption where applicable, and normalized final state match.

## Required inputs for a slice

Before writing Java, collect:

- Python source files that own the behavior.
- Direct Python imports used by that behavior.
- Existing Python tests touching the behavior.
- Runtime type-manifest observations when Python typing is ambiguous.
- Known ordering and rounding rules.
- RNG calls and their order.
- Minimal representative scenarios plus edge cases.

Do not start by translating the whole dependency graph. Resolve the smallest stable contract first.

## Migration loop

```text
Inspect Python behavior
        |
Define typed Java contract
        |
Export Python fixtures
        |
Implement Java slice
        |
Compile Java
   | fail -> fix type/API/build errors
   v
Run Java tests
   | fail -> fix local behavior
   v
Run Python-vs-Java differential tests
   | fail -> classify mismatch
   |         - input normalization
   |         - ordering
   |         - RNG consumption
   |         - rounding
   |         - hidden state/dependency
   |         - actual implementation bug
   v
Promote mismatch to regression fixture
        |
Mark slice parity-complete
```

## Source-to-target rule

Translation tools may generate a first Java draft only after the source contract and oracle fixture exist.

Generated code is never accepted because it compiles. It must pass the same parity gates as manually written code.

For highly dynamic Python code, prefer:

1. runtime type tracing,
2. a small typed Java record/interface boundary,
3. a pure resolver using that boundary,
4. an adapter that extracts the boundary from richer battle state.

This is the pattern already used by `MovementProfile`, `JumpProfile`, and the targeting models.

## Failure classification

When parity fails, record the failure under exactly one primary class before changing code:

- `TYPE_CONTRACT`: Java model cannot represent a Python value/state correctly.
- `INPUT_NORMALIZATION`: case/default/null/empty semantics differ.
- `ORDERING`: collection, initiative, hook, target, or event order differs.
- `RNG`: engines consume different random values or consume them in a different order.
- `ROUNDING`: `floor`, integer conversion, division, or modifier ordering differs.
- `STATE_DEPENDENCY`: the extracted contract omitted a status, ability, terrain, item, feature, or temporary state.
- `RULE_LOGIC`: Java rule implementation differs from Python.
- `ORACLE_FIXTURE`: fixture/test assumption was wrong; prove this against Python before changing Java.

Every real bug should produce a permanent regression test.

## Clean-room and licensing rule

Reference repositories are used for architecture and migration methodology unless their license is intentionally adopted.

Do not copy GPL implementation code into AutoPTU-Java. MIT-licensed references still should not replace behavioral validation against AutoPTU's own Python oracle.

## Priority order for translation assistance

Use automation in this order:

1. Search/read the Python source and tests.
2. Export runtime types when needed.
3. Generate focused Python oracle fixtures.
4. Write or generate a Java implementation for one bounded subsystem.
5. Compile and run Java tests.
6. Run differential parity.
7. Add regression scenarios for every mismatch.
8. Only then expand to the next dependency.

## Production boundary

`autoptu-core` must remain independent from Minecraft, Cobblemon, Craftics, FastAPI, and the old browser UI.

The eventual Minecraft integration should adapt Minecraft/Cobblemon state into AutoPTU Java DTOs and consume semantic battle events. It must not become the place where PTU legality is reimplemented.
