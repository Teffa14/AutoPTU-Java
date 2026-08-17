# Runtime Type Tracing

Python's dynamic types are one of the main risks when redesigning AutoPTU as a typed Java core. Guessing from annotations is not enough because much of the existing battle engine grew incrementally and many important contracts are implicit.

`tools/python/export_runtime_types.py` records the concrete type shapes observed while real Python tests execute.

It does **not** record argument values. The output is a structural manifest of function calls, argument types, container element types, return types, source files, and line numbers.

## Typical workflow

Assume both repositories are checked out next to each other:

```text
workspace/
  AutoPTU/
  AutoPTU-Java/
```

From `AutoPTU-Java`:

```bash
python tools/python/export_runtime_types.py \
  --source-root ../AutoPTU \
  --include auto_ptu/rules \
  --output build/python-rule-types.json \
  -- tests/test_battle_state.py -q
```

Use smaller test selections while designing one subsystem:

```bash
python tools/python/export_runtime_types.py \
  --source-root ../AutoPTU \
  --include auto_ptu/rules \
  --output build/movement-types.json \
  -- tests/test_battle_state.py -k "movement or shift or jump" -q
```

## How the manifest helps the Java port

For each observed Python function the manifest records examples such as:

```json
{
  "qualname": "BattleState.some_rule",
  "calls": 84,
  "arguments": {
    "actor_id": ["builtins.str"],
    "targets": ["builtins.list[builtins.str]"]
  },
  "returns": ["builtins.bool"]
}
```

That evidence helps decide whether Java needs:

- a primitive/value type,
- a record,
- an enum,
- a collection with one stable element type,
- a sealed hierarchy,
- or a deliberately polymorphic boundary.

The manifest is advisory. Python runtime observations cannot prove that unobserved types never occur. Every Java contract still needs scenario and parity tests.

## Why this is preferable to wholesale transpilation

A mechanical Python-to-Java translator has to guess types and preserve Python object semantics inside generated Java. AutoPTU has too many coupled battle interactions for that to be a safe production architecture.

The tracer uses execution evidence only to make the manual/assisted Java redesign less speculative. Python remains the behavior oracle; Java remains intentionally typed and idiomatic.
