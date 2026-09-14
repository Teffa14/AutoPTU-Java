package io.autoptu.core.runtime;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Server-authoritative usage history for reaction families with per-round limits.
 *
 * <p>The key space is generic: content systems provide a stable reaction key while the
 * runtime owns combatant identity, round indexing, recording, and lifecycle pruning.</p>
 */
public final class ReactionUsageState {
    private final LinkedHashMap<Key, LinkedHashMap<Integer, Integer>> usesByKey = new LinkedHashMap<>();

    public int usesThisRound(String combatantId, String reactionKey, int round) {
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
        Key key = new Key(combatantId, reactionKey);
        return usesByKey.getOrDefault(key, new LinkedHashMap<>()).getOrDefault(round, 0);
    }

    /** Read-only deterministic snapshot for parity fixtures and diagnostics. */
    public Map<String, Integer> snapshotForRound(int round) {
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
        LinkedHashMap<String, Integer> snapshot = new LinkedHashMap<>();
        for (Map.Entry<Key, LinkedHashMap<Integer, Integer>> entry : usesByKey.entrySet()) {
            int uses = entry.getValue().getOrDefault(round, 0);
            if (uses > 0) {
                snapshot.put(entry.getKey().combatantId() + "\t" + entry.getKey().reactionKey(), uses);
            }
        }
        return Map.copyOf(snapshot);
    }

    /** Runtime-only producer boundary. Execution records a use only after the reaction commits. */
    void recordUseFromRuntime(String combatantId, String reactionKey, int round) {
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
        Key key = new Key(combatantId, reactionKey);
        LinkedHashMap<Integer, Integer> rounds = usesByKey.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        rounds.merge(round, 1, Integer::sum);
    }

    /** Lifecycle-only cleanup. Old rounds cannot influence eligibility in the current round. */
    void pruneForRoundFromLifecycle(int currentRound) {
        if (currentRound < 0) throw new IllegalArgumentException("currentRound cannot be negative");
        usesByKey.values().forEach(rounds -> rounds.keySet().removeIf(round -> round < currentRound));
        usesByKey.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private record Key(String combatantId, String reactionKey) {
        private Key {
            if (combatantId == null || combatantId.isBlank()) {
                throw new IllegalArgumentException("combatantId is required");
            }
            if (reactionKey == null || reactionKey.isBlank()) {
                throw new IllegalArgumentException("reactionKey is required");
            }
            combatantId = combatantId.strip();
            reactionKey = reactionKey.strip().toLowerCase(Locale.ROOT);
        }
    }
}
