package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.List;

/** Executes matured delayed hits from server-owned queue/RNG state. */
public final class DelayedHitLifecycleExecutor {
    private static final MoveResolutionInput NEUTRAL_LEGACY_INPUT = new MoveResolutionInput(
            2, 0, 0, 20, false, false, false,
            8, 0, 0, false, 1.0, List.of()
    );

    private DelayedHitLifecycleExecutor() {
    }

    /** Preferred lifecycle boundary: queue and RNG are both owned by BattleRuntimeState. */
    static List<BattleEvent> resolveDueCombatantHits(BattleRuntimeState state, int currentRound) {
        if (state == null) throw new IllegalArgumentException("state is required");
        return resolveDueCombatantHits(state, state.delayedHitStateFromRuntime(), currentRound);
    }

    /**
     * Transitional injection seam retained for focused parity tests.
     * Production/lifecycle code should use the BattleRuntimeState-owned overload.
     */
    @Deprecated
    static List<BattleEvent> resolveDueCombatantHits(
            BattleRuntimeState state,
            BattleDelayedHitState delayedState,
            int currentRound
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (delayedState == null) throw new IllegalArgumentException("delayedState is required");
        if (currentRound < 0) throw new IllegalArgumentException("currentRound cannot be negative");
        if (currentRound != state.currentRound()) {
            throw new IllegalArgumentException("delayed lifecycle round does not match BattleRuntimeState");
        }

        // Preflight every due entry before takeDue mutates the server-owned queue. Position-only
        // TILE/area requests remain intentionally unsupported in this slice. A stale combatant id,
        // however, is now safe to expand through the authoritative target resolver using its stored
        // anchor, matching the pinned Python fallback contract.
        for (DelayedHitEntry entry : delayedState.entriesInInsertionOrder()) {
            if (entry.triggerRound() > currentRound) continue;
            DelayedHitTargetRequest targetRequest = DelayedHitBindingResolver.resolveTargetRequest(state, entry);
            if (targetRequest.positionOnly()) {
                throw new UnsupportedOperationException(
                        "due TILE/area delayed hits require the dedicated target-resolution parity slice"
                );
            }
            if (targetRequest.missingStoredCombatant()) {
                DelayedHitBindingResolver.bindEffectiveTargets(state, targetRequest);
            }
        }

        DelayedHitBatch batch = delayedState.takeDueFromLifecycle(currentRound);
        ArrayList<BattleEvent> events = new ArrayList<>();
        for (DelayedHitEntry entry : batch.due()) {
            DelayedHitTargetRequest targetRequest = DelayedHitBindingResolver.resolveTargetRequest(state, entry);
            if (targetRequest.missingStoredCombatant()) {
                for (DelayedHitBinding binding : DelayedHitBindingResolver.bindEffectiveTargets(state, targetRequest)) {
                    events.addAll(resolveBinding(state, delayedState, entry, binding));
                }
                continue;
            }
            DelayedHitBinding binding = DelayedHitBindingResolver.bind(state, entry);
            events.addAll(resolveBinding(state, delayedState, entry, binding));
        }
        return List.copyOf(events);
    }

    private static List<BattleEvent> resolveBinding(
            BattleRuntimeState state,
            BattleDelayedHitState delayedState,
            DelayedHitEntry entry,
            DelayedHitBinding binding
    ) {
        AppliedActionResult result = RuntimeMoveResolution.applyDelayedUsingAuthoritativeCombatState(
                state,
                binding,
                entry.effect().isBlank() ? "Delayed" : entry.effect(),
                delayedState.randomFromRuntime(),
                NEUTRAL_LEGACY_INPUT,
                false,
                false
        );
        return result.events();
    }
}
