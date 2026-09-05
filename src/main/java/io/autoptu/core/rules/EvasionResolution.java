package io.autoptu.core.rules;

import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;

import java.util.Locale;

/**
 * Pure PTU Evasion projection.
 *
 * PTU derives Stat Evasion from the effective Defense, Special Defense, or
 * Speed Stat after Combat Stages and other stat-changing rules are applied.
 * Stat-derived Evasion is capped at +6; the final positive Evasion contribution
 * to an Accuracy Check is capped at +9. Negative non-stat Evasion may erase
 * positive Evasion but may not make an attack easier than its base AC.
 */
public final class EvasionResolution {
    private EvasionResolution() {
    }

    public static int resolve(EvasionProfile profile, String category) {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required");
        }
        String normalized = normalize(category);
        CombatantStatProfile stats = profile.stats();

        int effectiveStat;
        if ("physical".equals(normalized)) {
            effectiveStat = StatResolution.defensive(stats, "physical", false);
        } else if ("status".equals(normalized)) {
            effectiveStat = StatResolution.speed(stats);
        } else {
            effectiveStat = StatResolution.defensive(stats, "special", false);
        }

        int statEvasion = Math.min(6, Math.max(0, effectiveStat / 5));

        int bonus = profile.bonusFor(normalized);
        if (profile.suppressPositiveBonuses()) {
            bonus = Math.min(0, bonus);
        }
        if (profile.ignoreNonStatBonuses()) {
            bonus = 0;
        }

        // PTU: Evasion may raise an Accuracy Check by at most +9. Negative
        // Evasion can erase positive Evasion, but cannot reduce the move's base AC.
        return Math.max(0, Math.min(9, statEvasion + bonus));
    }

    private static String normalize(String category) {
        return category == null ? "special" : category.strip().toLowerCase(Locale.ROOT);
    }
}
