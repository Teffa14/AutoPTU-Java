package io.autoptu.core.rules;

import io.autoptu.core.model.EvasionProfile;

import java.util.Collection;
import java.util.Locale;

/**
 * Applies status-derived evasion semantics before the pure evasion formula runs.
 * Canonical battle status state, not Minecraft/Cobblemon entity data, decides
 * whether positive evasion bonuses are suppressed.
 */
public final class StatusEvasionResolution {
    private StatusEvasionResolution() {
    }

    public static EvasionProfile apply(EvasionProfile profile, Collection<String> statuses) {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required");
        }
        boolean sleeping = hasStatus(statuses, "sleep") || hasStatus(statuses, "asleep");
        if (!sleeping || profile.suppressPositiveBonuses()) {
            return profile;
        }
        return new EvasionProfile(
                profile.stats(),
                profile.physicalBonus(),
                profile.specialBonus(),
                profile.statusBonus(),
                true,
                profile.ignoreNonStatBonuses()
        );
    }

    private static boolean hasStatus(Collection<String> statuses, String expected) {
        if (statuses == null || statuses.isEmpty()) {
            return false;
        }
        String normalizedExpected = expected.toLowerCase(Locale.ROOT);
        for (String status : statuses) {
            if (status != null && status.strip().toLowerCase(Locale.ROOT).equals(normalizedExpected)) {
                return true;
            }
        }
        return false;
    }
}
