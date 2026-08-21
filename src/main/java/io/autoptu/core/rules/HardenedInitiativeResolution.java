package io.autoptu.core.rules;

import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.List;

/**
 * Resolves the PTU Hardened initiative bonus from server-owned semantic state.
 *
 * Python requires at least three injuries and an active Hardened temporary effect.
 * The normal initiative bonus is +5. Press On! doubles Hardened bonuses only while
 * press_on_active is present and the controlling Trainer has Intimidate rank 6+.
 */
public final class HardenedInitiativeResolution {
    private HardenedInitiativeResolution() {
    }

    public static int resolve(
            int currentRound,
            int injuries,
            List<TemporaryEffectEntry> temporaryEffects,
            boolean hasPressOnFeature,
            int intimidateRank
    ) {
        if (currentRound < 0) {
            throw new IllegalArgumentException("currentRound cannot be negative");
        }
        if (injuries < 0) {
            throw new IllegalArgumentException("injuries cannot be negative");
        }
        List<TemporaryEffectEntry> effects = temporaryEffects == null ? List.of() : List.copyOf(temporaryEffects);
        if (injuries < 3 || !hasActiveHardened(currentRound, effects)) {
            return 0;
        }

        boolean pressingOn = effects.stream().anyMatch(entry -> entry.name().equals("press_on_active"));
        int multiplier = hasPressOnFeature && pressingOn && intimidateRank >= 6 ? 2 : 1;
        return 5 * multiplier;
    }

    static boolean hasActiveHardened(int currentRound, List<TemporaryEffectEntry> effects) {
        for (TemporaryEffectEntry entry : effects) {
            if (!entry.name().equals("hardened")) {
                continue;
            }
            Object expiry = entry.payload().get("expires_round");
            if (expiry == null) {
                return true;
            }
            Integer parsedExpiry = pythonStyleInt(expiry);
            // Python catches TypeError/ValueError and keeps the malformed Hardened entry active.
            if (parsedExpiry == null || currentRound <= parsedExpiry) {
                return true;
            }
        }
        return false;
    }

    private static Integer pythonStyleInt(Object value) {
        try {
            if (value instanceof Integer integer) return integer;
            if (value instanceof Long longValue) return Math.toIntExact(longValue);
            if (value instanceof Double doubleValue) return doubleValue.intValue();
            if (value instanceof Boolean bool) return bool ? 1 : 0;
            if (value instanceof String text) return Integer.parseInt(text.strip());
            return null;
        } catch (ArithmeticException | NumberFormatException ignored) {
            return null;
        }
    }
}
