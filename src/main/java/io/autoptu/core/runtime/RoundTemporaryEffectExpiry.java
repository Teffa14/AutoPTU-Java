package io.autoptu.core.runtime;

import java.util.List;

/**
 * Python-compatible ROUND_START expiry for temporary effects carrying until_round.
 *
 * Python iterates a snapshot of matching entries and calls remove_temporary_effect(name),
 * which removes the first live occurrence rather than the specific snapshot entry. This
 * resolver deliberately preserves that observable multiplicity behavior.
 */
public final class RoundTemporaryEffectExpiry {
    private RoundTemporaryEffectExpiry() {}

    public static int expireFamily(BattleRuntimeState state, int currentRound, String effectName) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (currentRound < 0) throw new IllegalArgumentException("currentRound cannot be negative");
        if (effectName == null || effectName.isBlank()) {
            throw new IllegalArgumentException("effectName is required");
        }

        int removed = 0;
        for (String combatantId : state.combatantIds()) {
            TemporaryEffectStore store = state.requireCombatant(combatantId).temporaryEffects();
            List<TemporaryEffectEntry> snapshot = store.getAll(effectName);
            for (TemporaryEffectEntry entry : snapshot) {
                Long expiry = pythonIntOrNull(entry.payload().get("until_round"));
                if (expiry == null) continue;
                if ((long) currentRound > expiry && store.removeFirst(effectName)) {
                    removed += 1;
                }
            }
        }
        return removed;
    }

    private static Long pythonIntOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool ? 1L : 0L;
        if (value instanceof Integer number) return number.longValue();
        if (value instanceof Long number) return number;
        if (value instanceof Double number) {
            if (!Double.isFinite(number)) return null;
            if (number > Long.MAX_VALUE || number < Long.MIN_VALUE) return null;
            return (long) number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.strip());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
