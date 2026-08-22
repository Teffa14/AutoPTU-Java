package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.List;

/** Executes matured combatant-target delayed hits from server-owned queue/RNG state. */
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

        // Preflight every due entry before takeDue mutates the server-owned queue. Python can
        // fall back to the stored position when a delayed combatant target disappeared, but
        // Java does not yet execute that target-resolution branch. Preserve the entry until
        // that parity slice exists rather than losing it after a failed direct binding.
        for (DelayedHitEntry entry : delayedState.entriesInInsertionOrder()) {
            if (entry.triggerRound() > currentRound) continue;
            DelayedHitTargetRequest targetRequest = DelayedHitBindingResolver.resolveTargetRequest(state, entry);
            if (targetRequest.positionOnly()) {
                throw new UnsupportedOperationException(
                        "due TILE/area delayed hits require the dedicated target-resolution parity slice"
                );
            }
            if (targetRequest.missingStoredCombatant()) {
                throw new UnsupportedOperationException(
                        "due delayed hit with missing combatant target requires stored-position target resolution"
                );
            }
        }

        DelayedHitBatch batch = delayedState.takeDueFromLifecycle(currentRound);
        ArrayList<BattleEvent> events = new ArrayList<>();
        for (DelayedHitEntry entry : batch.due()) {
            DelayedHitBinding binding = DelayedHitBindingResolver.bind(state, entry);
            AppliedActionResult result = RuntimeMoveResolution.applyDelayedUsingAuthoritativeCombatState(
                    state,
                    binding,
                    entry.effect().isBlank() ? "Delayed" : entry.effect(),
                    delayedState.randomFromRuntime(),
                    NEUTRAL_LEGACY_INPUT,
                    false,
                    false
            );
            events.addAll(result.events());
        }
        return List.copyOf(events);
    }
}
