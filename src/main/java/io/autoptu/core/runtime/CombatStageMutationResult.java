package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of one authoritative PTU combat-stage mutation.
 *
 * The base mutation is clamped before reaction hooks run. Reaction hooks may then
 * mutate the same combat stage again, so both the base and final stage are retained.
 */
public record CombatStageMutationResult(
        int startingStage,
        int requestedDelta,
        int baseAppliedDelta,
        int baseStage,
        int finalStage,
        List<BattleEvent> events
) {
    public CombatStageMutationResult {
        if (events == null || events.isEmpty()) {
            events = List.of();
        } else {
            ArrayList<BattleEvent> copy = new ArrayList<>(events.size());
            for (BattleEvent event : events) {
                if (event == null) {
                    throw new IllegalArgumentException("combat-stage mutation event cannot be null");
                }
                copy.add(event);
            }
            events = List.copyOf(copy);
        }
    }
}
