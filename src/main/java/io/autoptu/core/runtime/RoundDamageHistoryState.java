package io.autoptu.core.runtime;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-owned round-scoped damage history used by abilities, Features and reactions.
 *
 * Python records the damaged target in damage_this_round, tracks attacker ids per target
 * in damage_taken_from, and accumulates the target's actual HP loss in
 * damage_received_this_round. PhaseController.start_round() rotates the first two
 * collections into previous-round snapshots and clears all current-round observations.
 */
public final class RoundDamageHistoryState {
    private final LinkedHashSet<String> damageThisRound = new LinkedHashSet<>();
    private final LinkedHashMap<String, LinkedHashSet<String>> damageTakenFromThisRound = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> damageReceivedThisRound = new LinkedHashMap<>();

    private Set<String> damageLastRound = Set.of();
    private Map<String, Set<String>> damageTakenFromLastRound = Map.of();

    /** Record a target in Python's damage_this_round set. */
    public void recordDamageThisRound(String combatantId) {
        damageThisRound.add(requireId(combatantId));
    }

    /** Record one source in Python's damage_taken_from[target] set. */
    public void recordDamageTakenFrom(String targetId, String sourceId) {
        String target = requireId(targetId);
        String source = requireId(sourceId);
        damageTakenFromThisRound.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(source);
    }

    /**
     * Accumulate actual HP loss for a target. Zero is meaningful for a move that hit but
     * dealt no HP loss, so the target key is still materialized just like Python.
     */
    public void recordDamageReceivedThisRound(String combatantId, int amount) {
        String target = requireId(combatantId);
        if (amount < 0) {
            throw new IllegalArgumentException("damage amount cannot be negative");
        }
        damageReceivedThisRound.merge(target, amount, Integer::sum);
    }

    /** Compatibility helper representing a zero-damage hit. */
    public void recordDamageReceivedThisRound(String combatantId) {
        recordDamageReceivedThisRound(combatantId, 0);
    }

    /**
     * Python BattleState._record_damage_exchange(attacker_id, target_id): the target is
     * marked as damaged this round and the attacker is stored as one of that target's
     * damage sources.
     */
    public void recordDamageExchange(String attackerId, String targetId) {
        String target = requireId(targetId);
        damageThisRound.add(target);
        if (attackerId != null && !attackerId.isBlank()) {
            recordDamageTakenFrom(target, attackerId);
        }
    }

    /** Record the complete ordinary-hit history update used by the authoritative move path. */
    public void recordMoveHit(String attackerId, String targetId, int actualDamage) {
        recordDamageReceivedThisRound(targetId, actualDamage);
        recordDamageExchange(attackerId, targetId);
    }

    public Set<String> damageThisRound() {
        return Set.copyOf(damageThisRound);
    }

    public Map<String, Set<String>> damageTakenFromThisRound() {
        return immutableDeepCopy(damageTakenFromThisRound);
    }

    public Map<String, Integer> damageReceivedThisRound() {
        return Map.copyOf(damageReceivedThisRound);
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
