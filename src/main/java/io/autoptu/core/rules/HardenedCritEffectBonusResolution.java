package io.autoptu.core.rules;

import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.List;

/**
 * Resolves the PTU Hardened critical/effect-range bonus from server-owned semantic state.
 *
 * <p>The pinned Python oracle grants this bonus while Hardened is active and the
 * combatant has at least one injury. The base bonus is +1. Press On! doubles the
 * Hardened bonus only while {@code press_on_active} is present and the controlling
 * Trainer has Intimidate rank 6+.</p>
 */
public final class HardenedCritEffectBonusResolution {
    private HardenedCritEffectBonusResolution() {
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

        List<TemporaryEffectEntry> effects = temporaryEffects == null
                ? List.of()
                : List.copyOf(temporaryEffects);
        if (injuries < 1 || !HardenedInitiativeResolution.hasActiveHardened(currentRound, effects)) {
            return 0;
        }

        boolean pressingOn = effects.stream().anyMatch(entry -> entry.name().equals("press_on_active"));
        return hasPressOnFeature && pressingOn && intimidateRank >= 6 ? 2 : 1;
    }
}
