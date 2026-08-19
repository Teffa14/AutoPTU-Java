package io.autoptu.core.runtime;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-owned round-scoped damage history used by abilities, Features and reactions.
 *
 * Python rotates damage_this_round and damage_taken_from into previous-round snapshots
 * during PhaseController.start_round(), then clears the current-round collections.
 * Keeping this state in the headless runtime prevents Minecraft/Cobblemon from becoming
 * the authority for rules that ask who dealt or received damage in the previous round.
 */
public final class RoundDamageHistoryState {
    private final LinkedHashSet<String> damageThisRound = new LinkedHashSet<>();
    private final LinkedHashMap<String, LinkedHashSet<String>> damageTakenFromThisRound = new LinkedHashMap<>();
    private final LinkedHashSet<String> damageReceivedThisRound = new LinkedHashSet<>();

    private Set<String> damageLastRound = Set.of();
    private Map<String, Set<String>> damageTakenFromLastRound = Map.of();

    public void recordDamageThisRound(String combatantId) {
        damageThisRound.add(requireId(combatantId));
    }

    public void recordDamageTakenFrom(String targetId, String sourceId) {
        String target = requireId(targetId);
        String source = requireId(sourceId);
        damageTakenFromThisRound.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(source);
    }

    public void recordDamageReceivedThisRound(String combatantId) {
        damageReceivedThisRound.add(requireId(combatantId));
    }

    public Set<String> damageThisRound() {
        return Set.copyOf(damageThisRound);
    }

    public Map<String, Set<String>> damageTakenFromThisRound() {
        return immutableDeepCopy(damageTakenFromThisRound);
    }

    public Set<String> damageReceivedThisRound() {
        return Set.copyOf(damageReceivedThisRound);
    }

    public Set<String> damageLastRound() {
        return damageLastRound;
    }

    public Map<String, Set<String>> damageTakenFromLastRound() {
        return damageTakenFromLastRound;
    }

    /**
     * Rotate current damage history exactly once at authoritative round start.
     * damage_received_this_round has no Python previous-round mirror and is only cleared.
     */
    public void rotateForNewRound() {
        damageLastRound = Set.copyOf(damageThisRound);
        damageTakenFromLastRound = immutableDeepCopy(damageTakenFromThisRound);
        damageThisRound.clear();
        damageTakenFromThisRound.clear();
        damageReceivedThisRound.clear();
    }

    private static Map<String, Set<String>> immutableDeepCopy(
            Map<String, ? extends Set<String>> source
    ) {
        LinkedHashMap<String, Set<String>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends Set<String>> entry : source.entrySet()) {
            copied.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(copied);
    }

    private static String requireId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("combatantId is required");
        }
        return value.strip();
    }
}
