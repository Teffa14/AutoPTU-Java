package io.autoptu.core.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-owned round injury snapshots used by abilities, Trainer Features and AI policy.
 *
 * Python PhaseController.start_round() copies _injuries_last_round to
 * _injuries_previous_round, then snapshots each combatant's current injury count into
 * _injuries_last_round. Current injury counts are updated by authoritative rule code;
 * Minecraft/Cobblemon may display them but must not supply historical values.
 */
public final class RoundInjuryHistoryState {
    private final LinkedHashMap<String, Integer> currentInjuries = new LinkedHashMap<>();
    private Map<String, Integer> injuriesPreviousRound = Map.of();
    private Map<String, Integer> injuriesLastRound = Map.of();

    public void setCurrentInjuries(String combatantId, int injuries) {
        if (combatantId == null || combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId is required");
        }
        if (injuries < 0) {
            throw new IllegalArgumentException("injuries cannot be negative");
        }
        currentInjuries.put(combatantId.strip(), injuries);
    }

    public int currentInjuries(String combatantId) {
        if (combatantId == null || combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId is required");
        }
        return currentInjuries.getOrDefault(combatantId.strip(), 0);
    }

    public Map<String, Integer> currentInjuries() {
        return Map.copyOf(currentInjuries);
    }

    public Map<String, Integer> injuriesPreviousRound() {
        return injuriesPreviousRound;
    }

    public Map<String, Integer> injuriesLastRound() {
        return injuriesLastRound;
    }

    /** Rotate Python-style history without changing current injury counts. */
    public void rotateForNewRound() {
        injuriesPreviousRound = injuriesLastRound;
        injuriesLastRound = Map.copyOf(currentInjuries);
    }
}
