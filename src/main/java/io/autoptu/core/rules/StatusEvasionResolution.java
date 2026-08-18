package io.autoptu.core.rules;

import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.StatFlag;

import java.util.Collection;
import java.util.Locale;

/**
 * Applies status-derived evasion semantics before the pure evasion formula runs.
 * Canonical battle status state, not Minecraft/Cobblemon entity data, decides
 * sleep/freeze suppression and paralysis stage penalties.
 */
public final class StatusEvasionResolution {
    private StatusEvasionResolution() {
    }

    public static EvasionProfile apply(EvasionProfile profile, Collection<String> statuses) {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required");
        }

        boolean suppressingStatus = hasStatus(statuses, "sleep")
                || hasStatus(statuses, "asleep")
                || hasStatus(statuses, "frozen")
                || hasStatus(statuses, "freeze");
        boolean paralyzed = hasStatus(statuses, "paralyzed") || hasStatus(statuses, "paralyze");
        boolean suppressPositiveBonuses = profile.suppressPositiveBonuses() || suppressingStatus;
        CombatantStatProfile stats = profile.stats().withFlag(StatFlag.PARALYZED, paralyzed);

        if (stats == profile.stats() && suppressPositiveBonuses == profile.suppressPositiveBonuses()) {
            return profile;
        }
        return new EvasionProfile(
                stats,
                profile.physicalBonus(),
                profile.specialBonus(),
                profile.statusBonus(),
                suppressPositiveBonuses,
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
