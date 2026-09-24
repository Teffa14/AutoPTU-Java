# Authoritative move execution context contract

Oracle pin: `Teffa14/AutoPTU@16d228efa63aabecb67fa788959a359aac7f8f03`

This contract freezes the execution identity carried by the authoritative single-target move resolver while the Java port remains parity-bound to Python AutoPTU.

## Server-authoritative ownership

The PTU core owns declaration validation, action and frequency spending, PRE-damage reactions, RNG, accuracy, damage, HP/history mutation, and ordered battle events. Minecraft, Cobblemon, and Craftics adapters may request or render an execution. They do not select or reproduce these rules.

## Execution identities

| Identity | Declaration state | Spend ordinary action/frequency | Run PRE-damage reactions |
| --- | --- | --- | --- |
| `ORDINARY` | validate declared combatant target | yes | yes |
| `PRE_RESOLUTION_RESOLVED` | already validated before target replacement | yes | yes |
| `AREA_RESOLVED` | area declaration and target expansion already validated | no | yes |
| `DELAYED` | delayed binding already validated | no | no |
| `COMMITTED_REACTION` | committed reaction declaration already validated | no | yes |

`AREA_RESOLVED` and `COMMITTED_REACTION` intentionally remain different identities even though their current ownership projections overlap. Callers must provide an identity. The resolver must not infer identity from ownership booleans.

## Resolver invariant

`BattleRuntime.applyAuthoritativeMoveInternal` must receive one `MoveRuntimeExecutionContext`. It must obtain declaration validation, action-spend ownership, move-frequency ownership, and PRE-damage reaction ownership from that context. Parallel boolean parameters for those decisions are transitional debt and must be removed together so an invalid tuple cannot enter the resolver.

The ordinary, pre-resolution-resolved, area-resolved, delayed, and committed-reaction ingress paths must construct their named context explicitly. A committed reaction must then traverse the same accuracy, damage, RNG, hook, HP/history, and event pipeline as an ordinary single-target move without a second action/frequency spend.

## Parity-safe migration order

The compatibility projection `MoveRuntimeExecutionContext.spendOrdinaryMoveResources()` must remain available until the authoritative resolver and all affected ingress paths have migrated. Removing that projection before the resolver migration is complete is known to break the broad Java/Python parity matrix and must not be used as a precursor change.

The resolver migration is one coherent slice:

1. Replace `spendOrdinaryMoveResources`, `runPreDamageReactions`, and `declaredChoiceAlreadyValidated` parameters with one `MoveRuntimeExecutionContext` on every `applyAuthoritativeMoveInternal` overload.
2. Construct `ordinary()`, `preResolutionResolved()`, `areaResolved()`, and `delayed()` explicitly at their existing ingress paths. `committedReaction()` remains reserved for the committed-reaction ingress until that handoff is wired.
3. Call `executionContext.requireValidDeclaration(...)` before the first RNG draw.
4. Use `executionContext.runPreDamageReactions()` only to select the PRE-damage reaction window.
5. Use `executionContext.ownsActionSpend()` only when applying the action-budget mutation.
6. Use `executionContext.ownsMoveFrequency()` only when recording move-frequency use.
7. Preserve accuracy/evasion, RNG order, move-special ordering, post-damage hooks, forced movement, HP/history mutation, and semantic event order.
8. Only after this resolver slice passes the full relevant parity matrix may the deprecated combined projection be removed.

No ingress may infer an execution identity from the legacy ownership tuple. In particular, `AREA_RESOLVED` and `COMMITTED_REACTION` must remain distinguishable even when their current ownership values match.

## Parity gate for the migration

The migration is accepted only when existing Java move-runtime tests remain green and deterministic Python-oracle fixtures preserve ordered RNG consumption, accuracy/evasion result, hook ordering, final signed damage, HP/injury history, action/frequency state, ordered semantic events, and final battle state for every affected ingress.

The required regression surface includes ordinary damage ingress, action economy, RNG ownership, pre-resolution target replacement, multi-target area resolution, delayed-hit execution/resource ownership, move-special execution/effect rolls, PRE-damage reactions, status move execution, forced movement/interception, initiative/lifecycle interactions, Trainer Feature hooks, held-item hooks, temporary HP, and damage/history behavior.

No adapter-specific PTU branch is permitted as part of this migration.
