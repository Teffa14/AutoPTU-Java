# Authoritative move runtime dispatch contract

Python oracle: `Teffa14/AutoPTU@16d228efa63aabecb67fa788959a359aac7f8f03`.

This contract freezes the execution-identity seam required before committed reactions can enter the ordinary single-target damage pipeline.

## Server-authoritative rule

`BattleRuntime` selects declaration validation, action-spend ownership, move-frequency ownership, and PRE-damage reaction ownership from one `MoveRuntimeExecutionContext`. Callers and adapters must not infer execution identity from resource-spend or reaction booleans. Minecraft, Cobblemon, and Craftics may submit or render outcomes but do not reproduce these rules.

## Execution modes

| Mode | Declaration validation | Own action spend | Own move frequency | Pre-damage reactions | Resolution path |
| --- | --- | --- | --- | --- | --- |
| `ORDINARY` | full ordinary legality | yes | yes | yes | ordinary single-target |
| `PRE_RESOLUTION_RESOLVED` | declaration already validated | yes | yes | yes | ordinary single-target after target replacement |
| `AREA_RESOLVED` | area target already selected | no | no | yes | area target execution |
| `DELAYED` | delayed binding | no | no | no | delayed single-target |
| `COMMITTED_REACTION` | actor/target/move binding only | no | no | yes | ordinary single-target |

Action spending and move-frequency accounting are independent ownership dimensions even when every currently frozen mode gives them the same value. Production code must query `ownsActionSpend()` and `ownsMoveFrequency()` separately. The deprecated combined projection exists only while legacy resolver call sites are migrated and must not become part of a new contract.

`COMMITTED_REACTION` must not be routed through `AREA_RESOLVED` merely because both modes currently skip action spending and move-frequency accounting and run PRE-damage reactions.

## Resolver migration invariant

`BattleRuntime.applyAuthoritativeMoveInternal` must receive one `MoveRuntimeExecutionContext`. It must not receive parallel booleans for action spending, move-frequency accounting, PRE-damage reactions, or prior declaration validation.

The resolver must call `requireValidDeclaration(...)` exactly once for the selected execution identity. It must use `runPreDamageReactions()` to select PRE hooks, `ownsActionSpend()` only at the action-budget mutation point, and `ownsMoveFrequency()` only at the frequency-recording point. No caller may reconstruct these decisions independently.

Changing this dispatch seam must not alter accuracy/evasion, RNG draw order, move-special ordering, post-damage hooks, type multiplier handling, HP/injury history, forced-movement dispatch, or ordered semantic events.

## Committed-reaction invariants

A committed reaction has already paid declaration-time costs. Runtime execution therefore validates the frozen actor, target, and move binding exactly once and does not spend Standard, Swift, Shift, or move-frequency resources again.

After that validation, the attack uses the same ordinary single-target machinery for accuracy, evasion, damage RNG, move-special hooks, pre/post-damage hooks, damage application, HP and injury history, and semantic event ordering. The execution mode itself must not add an RNG draw.

## Required parity gate

The implementation slice that wires this contract into `BattleRuntime` must freeze a Python-oracle case and compare Java against it for:

1. action budget before and after execution;
2. move-frequency state before and after execution;
3. ordered RNG draws;
4. accuracy/evasion result;
5. ordered move-special and reaction hooks;
6. damage and HP/injury-history mutation;
7. ordered semantic battle events;
8. final battle state.

The gate must also include regression cases proving that `PRE_RESOLUTION_RESOLVED`, `AREA_RESOLVED`, and `DELAYED` retain their existing validation and ownership semantics. A regression test must fail if action-spend ownership and move-frequency ownership are collapsed back into one production decision.

## Adapter boundary

No adapter may bypass this dispatch by directly applying damage, statuses, resource consumption, or PTU legality. The authoritative core returns resolved state/events for adapters to render.
