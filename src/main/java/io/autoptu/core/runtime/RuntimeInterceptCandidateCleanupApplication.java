package io.autoptu.core.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Commits Python-compatible temporary-effect expiry discovered during interception candidate scanning.
 *
 * <p>The pure discovery resolver reports how many expired entries Python removed while scanning.
 * This application performs those removals against the authoritative temporary-effect stores while
 * preserving insertion order, multiplicity, and active entries. Minecraft/Cobblemon never performs
 * this cleanup.</p>
 */
public final class RuntimeInterceptCandidateCleanupApplication {
    private RuntimeInterceptCandidateCleanupApplication() {}

    public record Result(
            int attackerNoInterceptRemoved,
            Map<String, Integer> sentinelRemovedByCombatant
    ) {}

    public static Result apply(
            BattleRuntimeState state,
            String attackerId,
            InterceptCandidateDiscoveryResolution.Result discovery
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (attackerId == null || attackerId.isBlank()) throw new IllegalArgumentException("attackerId is required");
        if (discovery == null) throw new IllegalArgumentException("discovery result is required");

        RuntimeCombatantState attacker = state.requireCombatant(attackerId);
        int noInterceptRemoved = removeExpiredSnapshots(
                attacker.temporaryEffects(),
                "no_intercept",
                discovery.attackerNoInterceptRemovalCount(),
                state.currentRound()
        );

        LinkedHashMap<String, Integer> sentinelRemoved = new LinkedHashMap<>();
        for (String combatantId : state.combatantIds()) {
            int requested = discovery.sentinelRemovalCountByCombatant().getOrDefault(combatantId, 0);
            if (requested <= 0) continue;
            int removed = removeExpiredSnapshots(
                    state.requireCombatant(combatantId).temporaryEffects(),
                    "sentinel_stance",
                    requested,
                    state.currentRound()
            );
            if (removed > 0) sentinelRemoved.put(combatantId, removed);
        }

        return new Result(noInterceptRemoved, Map.copyOf(sentinelRemoved));
    }

    private static int removeExpiredSnapshots(
            TemporaryEffectStore store,
            String effectName,
            int requested,
            int currentRound
    ) {
        if (requested <= 0) return 0;
        List<TemporaryEffectEntry> snapshot = store.getAll(effectName);
        int removed = 0;
        for (TemporaryEffectEntry entry : snapshot) {
            if (removed >= requested) break;
            Integer expiresRound = integer(entry.payload().get("expires_round"));
            if (expiresRound == null || currentRound <= expiresRound) continue;
            if (store.removeEntry(entry)) removed += 1;
        }
        return removed;
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.strip());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
