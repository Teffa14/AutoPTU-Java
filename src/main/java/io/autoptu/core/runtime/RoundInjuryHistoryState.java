package io.autoptu.core.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-owned round injury snapshots used by abilities, Trainer Features and AI policy.
 *
 * Python PhaseController.start_round() copies _injuries_last_round to
 * _injuries_previous_round, then snapshots each combatant's current injury count into
 * _injuries_last_round. Keeping this state in the headless runtime prevents
 * Minecraft/Cobblemon from supplying previous-round injury history as a rule input.
 */
public final class RoundInjuryHistoryState {
    private Map<String, Integer> injuriesPreviousRound = Map.of();
    private Map<String, Integer> injuriesLastRound = Map.of();

    public Map<String, Integer> injuriesPreviousRound() {
        return injuriesPreviousRound;
    }

    public Map<String, Integer> injuriesLastRound() {
        return injuriesLastRound;
    }

    /** Rotate history and snapshot current canonical combatant injury counts. */
    public void rotateForNewRound(BattleRuntimeState state) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        injuriesPreviousRound = injuriesLastRound;
        LinkedHashMap<String, Integer> current = new LinkedHashMap<>();
        for (String combatantId : state.combatantIds()) {
            current.put(combatantId, state.injuries(combatantId));
        }
        injuriesLastRound = Map.copyOf(current);
    }
}
