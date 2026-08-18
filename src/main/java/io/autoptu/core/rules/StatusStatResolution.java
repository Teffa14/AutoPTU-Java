package io.autoptu.core.rules;

import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.StatFlag;

import java.util.Collection;
import java.util.Locale;

/**
 * Projects canonical battle statuses into the stat flags consumed by StatResolution.
 * Minecraft/Cobblemon entity metadata is presentation state and cannot decide these flags.
 */
public final class StatusStatResolution {
    private StatusStatResolution() {
    }

    public static CombatantStatProfile apply(CombatantStatProfile profile, Collection<String> statuses) {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required");
        }

        boolean burned = hasAny(statuses, "burned", "burn");
        boolean poisoned = hasAny(statuses, "poisoned", "poison", "badly poisoned", "badly_poisoned");
        boolean paralyzed = hasAny(statuses, "paralyzed", "paralyze");
        boolean majorStatus = burned
                || poisoned
                || paralyzed
                || hasAny(statuses, "sleep", "asleep", "frozen", "freeze");

        return profile
                .withFlag(StatFlag.BURNED, burned)
                .withFlag(StatFlag.POISONED, poisoned)
                .withFlag(StatFlag.PARALYZED, paralyzed)
                .withFlag(StatFlag.MAJOR_STATUS, majorStatus);
    }

    private static boolean hasAny(Collection<String> statuses, String... expected) {
        if (statuses == null || statuses.isEmpty()) {
            return false;
        }
        for (String status : statuses) {
            if (status == null || status.isBlank()) {
                continue;
            }
            String normalized = status.strip().toLowerCase(Locale.ROOT);
            for (String candidate : expected) {
                if (normalized.equals(candidate)) {
                    return true;
                }
            }
        }
        return false;
    }
}
