package io.autoptu.core.rules;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.StatFlag;
import io.autoptu.core.model.StatModifier;

import java.util.Locale;

/** Pure counterpart of Python calculations.evasion_value. */
public final class EvasionResolution {
    private EvasionResolution() {
    }

    public static int resolve(EvasionProfile profile, String category) {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required");
        }
        String normalized = normalize(category);
        CombatantStatProfile stats = profile.stats();

        CombatStat stat;
        int rawBase;
        int stage = 0;
        if ("physical".equals(normalized)) {
            stat = CombatStat.DEF;
            rawBase = stats.base(stat);
            if (stats.has(StatFlag.HEAVY_METAL_ERRATA)) {
                rawBase += 2;
            }
            if (stats.has(StatFlag.LIGHT_METAL_ERRATA)) {
                rawBase = Math.max(1, rawBase - 2);
            }
        } else if ("status".equals(normalized)) {
            stat = CombatStat.SPD;
            rawBase = stats.base(stat);
            if (stats.has(StatFlag.HEAVY_METAL_ERRATA)) {
                rawBase = Math.max(1, rawBase - 2);
            }
            if (stats.has(StatFlag.LIGHT_METAL_ERRATA)) {
                rawBase = Math.max(1, rawBase + 2);
            }
            stage = stats.stage(CombatStat.SPD);
            if (stats.has(StatFlag.PARALYZED)) {
                stage -= 4;
            }
        } else {
            stat = CombatStat.SPDEF;
            rawBase = stats.base(stat);
        }

        StatModifier modifier = stats.modifier(stat);
        int adjustedBase = Math.max(1, rawBase + modifier.additive());
        adjustedBase = (int) Math.floor(adjustedBase * modifier.scalar());
        int baseEvasion = adjustedBase / 5;

        int bonus = profile.bonusFor(normalized);
        if (profile.suppressPositiveBonuses()) {
            bonus = Math.min(0, bonus);
        }
        if (profile.ignoreNonStatBonuses()) {
            bonus = 0;
        }
        return baseEvasion + bonus + stage;
    }

    private static String normalize(String category) {
        return category == null ? "special" : category.strip().toLowerCase(Locale.ROOT);
    }
}
