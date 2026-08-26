package io.autoptu.core.runtime;

import java.util.List;

/**
 * Pinned Python BattleState._chronicler_accuracy_bonus() contract.
 *
 * <p>The resolver owns expiry and stacking for targeted_profiling entries. Profile matching is
 * supplied by a server-owned matcher so Minecraft/Cobblemon never supplies the final Accuracy
 * bonus or a precomputed eligibility flag.</p>
 */
final class ChroniclerAccuracyBonusResolution {
    private ChroniclerAccuracyBonusResolution() {
    }

    @FunctionalInterface
    interface ProfileMatcher {
        boolean matches(String sourceControllerId);
    }

    static int resolve(
            TemporaryEffectStore temporaryEffects,
            int currentRound,
            String attackerControllerId,
            ProfileMatcher matcher
    ) {
        if (temporaryEffects == null) {
            throw new IllegalArgumentException("temporaryEffects is required");
        }
        if (matcher == null) {
            throw new IllegalArgumentException("matcher is required");
        }
        String fallbackController = attackerControllerId == null ? "" : attackerControllerId;
        int bonus = 0;
        List<TemporaryEffectEntry> snapshot = temporaryEffects.getAll("targeted_profiling");
        for (TemporaryEffectEntry entry : snapshot) {
            Integer expiresRound = intLike(entry.payload().get("expires_round"));
            if (expiresRound != null && currentRound > expiresRound) {
                temporaryEffects.removeEntry(entry);
                continue;
            }

            Object sourceValue = entry.payload().get("source_controller");
            String sourceController = sourceValue == null ? "" : String.valueOf(sourceValue);
            if (sourceController.isBlank()) {
                sourceController = fallbackController;
            }
            if (matcher.matches(sourceController)) {
                bonus += 2;
            }
        }
        return bonus;
    }

    private static Integer intLike(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
