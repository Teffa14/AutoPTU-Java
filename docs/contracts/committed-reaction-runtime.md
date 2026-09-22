# Committed reaction runtime contract

Oracle: `Teffa14/AutoPTU@16d228efa63aabecb67fa788959a359aac7f8f03`

This contract freezes the language-neutral boundary for executing a move whose reaction resource was already committed by the authoritative action-window layer.

## Inputs

The runtime receives one immutable committed-reaction execution value containing:

- the committed reactor, triggering combatant, target and move identity;
- authoritative RNG;
- the ordinary `MoveResolutionInput`;
- the move-special registry and effective move metadata;
- authoritative runtime dependencies, including pre-damage reactions and post-damage effects;
- line-of-sight state and any already-ordered pre-resolution events.

The runtime must reject any execution whose reactor or triggering combatant differs from the committed instruction. Actor, target and move identity must remain equal to the frozen binding.

The committed `MoveChoice` action type represents the reaction/action-window execution context and is not required to equal the move metadata's ordinary action type. Existing committed-reaction fixtures intentionally bind an ordinary Standard move to a Free reaction choice after the reaction resource has already been paid. Runtime code must not normalize that choice back to Standard or use the move's ordinary action type to charge a second action.

## Execution

A committed reaction move enters the same ordinary move-resolution pipeline used by an ordinary attack. Accuracy, evasion, deterministic damage arithmetic, RNG consumption, pre-damage reactions, damage application, injury/history updates, move-special effects, post-damage effects and semantic event ordering remain owned by `BattleRuntime`.

The reaction commit already paid the reaction/action-window resource. Runtime execution therefore uses `MoveRuntimeExecutionContext.committedReaction()`. That identity owns neither ordinary action spending nor move-frequency recording, runs PRE-damage reactions, and carries an already-validated declaration. It must not spend a Standard/Swift/Shift action again or consume move frequency a second time.

The committed-reaction handoff is already identity-validated by `CommittedReactionRuntimeExecutionGuard`. The ordinary resolver must therefore enter through an explicit already-validated reaction path. It must not fall through the existing area-target or delayed-hit validators. Those validators intentionally require ordinary move/action metadata relationships that do not hold for a committed reaction, where a Standard move may execute under a Free reaction `MoveChoice`. Reconstructing a Standard choice, cloning the move as Free, or otherwise changing either frozen value to satisfy those validators is forbidden because it changes hook-visible metadata or resource ownership.

### Runtime ingress dispatch

`BattleRuntime` must accept the prepared `CommittedReactionRuntimeExecutionPlan` as one server-owned value. `CommittedReactionRuntimeIngress` carries the plan's `MoveRuntimeExecutionContext` intact to the authoritative resolver boundary. New runtime wiring must consume `resolveExecutionContext(...)`; it must not reconstruct execution identity from the legacy ownership tuple or from `MoveRuntimeExecutionMode` alone.

The internal resolver obtains declaration validation, PRE-damage reaction ownership, action spending and move-frequency ownership only from that context. For `COMMITTED_REACTION`, declaration revalidation is skipped because the frozen binding was already validated, while accuracy, rerolls, damage RNG, move-special hooks, pre-damage reactions, post-damage hooks, HP/history mutation and ordered events execute unchanged. The ingress must delegate exactly once.

Legacy tuple accessors remain source-compatibility projections during migration. They are not an authoritative contract and may not be used to infer a mode. `AREA_RESOLVED` and `COMMITTED_REACTION` deliberately remain distinct identities even when current ownership projections overlap.

Minecraft, Cobblemon and Craftics adapters may submit or render the frozen contract. They must not recompute PTU legality, targeting, accuracy, damage, RNG, resource spending or state transitions.

## Required parity trace

The first end-to-end Attack of Opportunity fixture must compare Java with the pinned Python oracle for:

1. committed reactor, triggering combatant, target and move identity;
2. action-economy and move-frequency state before and after execution;
3. RNG draw count and draw order;
4. ordered semantic events;
5. HP, injuries, damage/history state, statuses and temporary effects;
6. move-special, ability, item, Trainer Feature and field hooks that fire on the ordinary damage path.

Acceptance requires compilation, Java tests, Python-oracle parity, rulebook conformance and deterministic trace parity. A green deterministic-damage test alone does not satisfy this contract.

## Deferred families

This contract does not claim parity for Interrupt/Priority reactions, reaction producers from abilities/items/Trainer Features/statuses/terrain/temporary effects, or the broader push/pull/knockback/interception families. Those require separate oracle-backed slices after the ordinary committed-reaction seam is proven.
