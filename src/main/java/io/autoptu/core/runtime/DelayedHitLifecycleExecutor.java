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

    /**
     * Resolve all due combatant-target delayed hits in insertion order.
     *
     * This slice intentionally fails before mutating the queue when a due TILE/area entry is
     * present. Python supports that path, but Java must freeze and port its target semantics
     * separately instead of silently treating a tile as a combatant.
     */
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

        for (DelayedHitEntry entry : delayedState.entriesInInsertionOrder()) {
            if (entry.triggerRound() <= currentRound && entry.targetId() == null) {
                throw new UnsupportedOperationException(
                        "due TILE/area delayed hits require the dedicated target-resolution parity slice"
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
