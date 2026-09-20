# Authoritative move runtime dispatch contract

Python oracle: `Teffa14/AutoPTU@16d228efa63aabecb67fa788959a359aac7f8f03`.

This contract freezes the execution-identity seam required before committed reactions can enter the ordinary single-target damage pipeline.

## Server-authoritative rule

`BattleRuntime` selects declaration validation and resource ownership from `MoveRuntimeExecutionMode`. Callers and adapters must not infer execution identity from resource-spend or reaction booleans. Minecraft, Cobblemon, and Craftics may submit or render outcomes but do not reproduce these rules.

## Execution modes

| Mode | Declaration validation | Spend ordinary action/frequency | Pre-damage reactions | Resolution path |
| --- | --- | --- | --- | --- |
| `ORDINARY` | full ordinary legality | yes | yes | ordinary single-target |
| `AREA_RESOLVED` | area target already selected | no | yes | area target execution |
| `DELAYED` | delayed binding | no | no | delayed single-target |
| `COMMITTED_REACTION` | actor/target/move binding only | no | yes | ordinary single-target |

`COMMITTED_REACTION` must not be routed through `AREA_RESOLVED` merely because both modes skip ordinary resource spending and run pre-damage reactions.

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

The gate must also include a regression case proving that `AREA_RESOLVED` and `DELAYED` retain their existing validation and ownership semantics.

## Adapter boundary

No adapter may bypass this dispatch by directly applying damage, statuses, resource consumption, or PTU legality. The authoritative core returns resolved state/events for adapters to render.