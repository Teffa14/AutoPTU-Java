package io.autoptu.core.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Commits Python-compatible temporary-effect expiry discovered during interception candidate scanning.
 *
 * <p>The pure discovery resolver reports how many expiry-triggered removals Python performed while
 * scanning a snapshot. PokemonState.remove_temporary_effect(name) removes the first live occurrence
 * of that family, so this application must preserve that first-family removal behavior even when the
 * expired snapshot that triggered the call is a later duplicate. Minecraft/Cobblemon never performs
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
        int noInterceptRemoved = removeFirstFamilyOccurrences(
                attacker.temporaryEffects(),
                "no_intercept",
                discovery.attackerNoInterceptRemovalCount()
        );

        LinkedHashMap<String, Integer> sentinelRemoved = new LinkedHashMap<>();
        for (String combatantId : state.combatantIds()) {
            int requested = discovery.sentinelRemovalCountByCombatant().getOrDefault(combatantId, 0);
            if (requested <= 0) continue;
            int removed = removeFirstFamilyOccurrences(
                    state.requireCombatant(combatantId).temporaryEffects(),
                    "sentinel_stance",
                    requested
            );
            if (removed > 0) sentinelRemoved.put(combatantId, removed);
        }

        return new Result(noInterceptRemoved, Map.copyOf(sentinelRemoved));
    }

    private static int removeFirstFamilyOccurrences(
            TemporaryEffectStore store,
            String effectName,
            int requested
    ) {
        if (requested <= 0) return 0;
        int removed = 0;
        while (removed < requested && store.removeFirst(effectName)) {
            removed += 1;
        }
        return removed;
    }
}
