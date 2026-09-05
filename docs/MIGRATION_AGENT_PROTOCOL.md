# AutoPTU Python -> Java Migration Agent Protocol

This document defines the acceptance loop for every AutoPTU subsystem port. It is inspired by repository-level translation systems such as RepoTransBench/RepoTransAgent, but it adds two stronger requirements: the existing Python AutoPTU engine is an executable oracle, and rule-bearing behavior must also be validated against the selected PTU rule profile.

## Completion states

Every port slice is tracked through independent gates:

1. `COMPILES` - the Java implementation and its tests compile.
2. `JAVA_TESTS_PASS` - targeted Java unit/regression tests pass.
3. `PYTHON_PARITY_PASS` - Java output matches the pinned Python oracle for the same scenarios.
4. `RULEBOOK_CONFORMANCE_PASS` - rule-bearing Java behavior matches the selected normative PTU/Kairos/Caelo rule profile.

A subsystem is not considered rule-complete until the applicable gates are true. Python parity by itself does not prove rules correctness.

For stateful battle slices, a fifth gate is required:

5. `TRACE_PARITY_PASS` - ordered semantic events, RNG consumption where applicable, and normalized final state match.

When the selected normative source and Python differ, use `RULEBOOK_DIVERGENCE_DOCUMENTED` instead of silently copying Python. See `docs/RULEBOOK_CONFORMANCE_AUDIT.md`.

## Required inputs for a slice

Before writing Java, collect:

- Exact normative source/profile and rulebook section/page for rule-bearing behavior.
- Python source files that own the behavior.
- Direct Python imports used by that behavior.
- Existing Python tests touching the behavior.
- Runtime type-manifest observations when Python typing is ambiguous.
- Known ordering and rounding rules.
- RNG calls and their order.
- Minimal representative scenarios plus edge cases.

If a requested rule profile source is missing, classify it as `SOURCE_REQUIRED`; do not infer or invent the rule.

Do not start by translating the whole dependency graph. Resolve the smallest stable contract first.

## Migration loop

```text
Inspect selected rulebook/profile
        |
Define language-neutral conformance contract
        |
Inspect Python behavior
        |
Define typed Java contract
        |
Freeze rulebook fixture + Python oracle fixture
        |
Implement Java slice
        |
Compile Java
   | fail -> fix type/API/build errors
   v
Run Java tests
   | fail -> fix local behavior
   v
Run rulebook conformance tests
   | fail -> classify Java rule bug/profile mismatch/ambiguity
   v
Run Python-vs-Java differential tests
   | fail -> classify mismatch
   |         - input normalization
   |         - ordering
   |         - RNG consumption
   |         - rounding
   |         - hidden state/dependency
   |         - Python/rulebook divergence
   |         - actual implementation bug
   v
Promote mismatch to regression fixture
        |
Mark slice complete for the selected profile
```

## Source hierarchy

Rules are profile-aware. Do not merge Kairos, Caelo and vanilla PTU semantics accidentally.

For a selected profile, apply this order:

1. Explicit project/campaign rule profile.
2. Applicable errata/clarifications for that profile.
3. Corresponding rulebook/core source.
4. Python AutoPTU as executable compatibility oracle.

Python remains authoritative for migration compatibility details that the rulebook does not define, such as internal semantic event payloads, serialization conventions, and implementation-specific ordering. It does not override an explicit normative combat rule without a documented project ruling.

## Source-to-target rule

Translation tools may generate a first Java draft only after the source contract, normative rule fixture where applicable, and oracle fixture exist.

Generated code is never accepted because it compiles. It must pass the same conformance and parity gates as manually written code.

For highly dynamic Python code, prefer:

1. runtime type tracing,
2. a small typed Java record/interface boundary,
3. a pure resolver using that boundary,
4. an adapter that extracts the boundary from richer battle state.

This is the pattern already used by `MovementProfile`, `JumpProfile`, and the targeting models.

## Failure classification

When validation fails, record the failure under exactly one primary class before changing code:

- `TYPE_CONTRACT`: Java model cannot represent a Python/rulebook value/state correctly.
- `INPUT_NORMALIZATION`: case/default/null/empty semantics differ.
- `ORDERING`: collection, initiative, hook, target, or event order differs.
- `RNG`: engines consume different random values or consume them in a different order.
- `ROUNDING`: `floor`, integer conversion, division, or modifier ordering differs.
- `STATE_DEPENDENCY`: the extracted contract omitted a status, ability, terrain, item, feature, or temporary state.
- `RULE_LOGIC`: Java rule implementation differs from the selected rule profile.
- `PYTHON_RULEBOOK_DIVERGENCE`: Python and the selected normative source disagree.
- `PROFILE_DIFFERENCE`: behavior is valid for one supported rule profile but not another.
- `RULE_AMBIGUITY`: source text is insufficient and requires an explicit project ruling.
- `SOURCE_REQUIRED`: requested normative source/version is not available.
- `ORACLE_FIXTURE`: fixture/test assumption was wrong; prove this against Python before changing Java.

Every resolved real bug or normative divergence should produce a permanent regression test.

## Clean-room and licensing rule

Reference repositories are used for architecture and migration methodology unless their license is intentionally adopted.

Do not copy GPL implementation code into AutoPTU-Java. MIT-licensed references still should not replace behavioral validation against AutoPTU's own Python oracle and the selected PTU rule profile.

## Priority order for translation assistance

Use automation in this order:

1. Search/read the selected rulebook/profile and record section/page.
2. Search/read the Python source and tests.
3. Export runtime types when needed.
4. Generate focused normative conformance fixtures and Python oracle fixtures.
5. Write or generate a Java implementation for one bounded subsystem.
6. Compile and run Java tests.
7. Run rulebook conformance.
8. Run differential Python parity.
9. Add regression scenarios for every mismatch/divergence.
10. Only then expand to the next dependency.

## Production boundary

`autoptu-core` must remain independent from Minecraft, Cobblemon, Craftics, FastAPI, and the old browser UI.

The eventual Minecraft integration should adapt Minecraft/Cobblemon state into AutoPTU Java DTOs and consume semantic battle events. It must not become the place where PTU legality is reimplemented.
