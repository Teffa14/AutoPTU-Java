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

`BattleRuntime.applyAuthoritativeMoveInternal` must receive one `MoveRuntimeExecutionContext`. It must obtain declaration validation, ordinary resource ownership, and PRE-damage reaction ownership from that context. Parallel boolean parameters for those decisions are transitional debt and must be removed together so an invalid tuple cannot enter the resolver.

The ordinary, pre-resolution-resolved, area-resolved, delayed, and committed-reaction ingress paths must construct their named context explicitly. A committed reaction must then traverse the same accuracy, damage, RNG, hook, HP/history, and event pipeline as an ordinary single-target move without a second action/frequency spend.

## Parity gate for the migration

The migration is accepted only when existing Java move-runtime tests remain green and deterministic Python-oracle fixtures preserve ordered RNG consumption, accuracy/evasion result, hook ordering, final signed damage, HP/injury history, action/frequency state, ordered semantic events, and final battle state for every affected ingress.

No adapter-specific PTU branch is permitted as part of this migration.
