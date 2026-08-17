package io.autoptu.core.rules;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.StatFlag;
import io.autoptu.core.model.StatModifier;

import java.util.Locale;

/** Pure counterpart of Python offensive_stat, defensive_stat, and speed_stat. */
public final class StatResolution {
    private StatResolution() {
    }

    public static int offensive(
            CombatantStatProfile profile,
            String category,
            boolean ignorePositiveStage
    ) {
        requireProfile(profile);
        String normalized = normalizeCategory(category);
        CombatStat stat;
        int stage;
        int base;

        if ("physical".equals(normalized)) {
            stat = CombatStat.ATK;
            if (profile.has(StatFlag.POWER_SHIFT) || profile.has(StatFlag.POWER_TRICK)) {
                stat = CombatStat.DEF;
            }
            stage = profile.stage(stat);
            base = rawBaseWithPhysicalDefenseMetal(profile, stat, true);
        } else {
            stat = profile.has(StatFlag.POWER_SHIFT) ? CombatStat.SPDEF : CombatStat.SPATK;
            stage = profile.stage(stat);
            base = profile.base(stat);
            if (profile.has(StatFlag.FLARE_BOOST) && profile.has(StatFlag.BURNED)) {
                stage = Math.min(6, stage + 2);
            }
        }

        return apply(profile, stat, base, stage, ignorePositiveStage);
    }

    public static int defensive(
            CombatantStatProfile profile,
            String category,
            boolean ignorePositiveStage
    ) {
        requireProfile(profile);
        String normalized = normalizeCategory(category);
        CombatStat stat;
        int stage;
        int base;

        if ("physical".equals(normalized)) {
            stat = profile.has(StatFlag.WONDER_ROOM) ? CombatStat.SPDEF : CombatStat.DEF;
            if (profile.has(StatFlag.POWER_SHIFT) || profile.has(StatFlag.POWER_TRICK)) {
                stat = CombatStat.ATK;
            }
            stage = profile.stage(stat);
            base = rawBaseWithPhysicalDefenseMetal(profile, stat, true);
            if (profile.has(StatFlag.BURNED)) {
                stage -= 2;
            }
        } else {
            stat = profile.has(StatFlag.WONDER_ROOM) ? CombatStat.DEF : CombatStat.SPDEF;
            if (profile.has(StatFlag.POWER_SHIFT)) {
                stat = CombatStat.SPATK;
            }
            stage = profile.stage(stat);
            base = profile.base(stat);
            // Preserve Python's special-defense Wonder Room quirk: Heavy Metal
            // applies to a selected DEF base here, while Light Metal does not.
            if (stat == CombatStat.DEF && profile.has(StatFlag.HEAVY_METAL_ERRATA)) {
                base += 2;
            }
            if (profile.has(StatFlag.POISONED) && !profile.has(StatFlag.POTENT_VENOM_OVERRIDE)) {
                stage -= 2;
            }
        }

        return apply(profile, stat, base, stage, ignorePositiveStage);
    }

    public static int speed(CombatantStatProfile profile) {
        requireProfile(profile);
        CombatStat stat = CombatStat.SPD;
        int base = profile.base(stat);
        int stage = profile.stage(stat);
        boolean quickFeetActive = profile.has(StatFlag.QUICK_FEET) && profile.has(StatFlag.MAJOR_STATUS);

        if (quickFeetActive) {
            stage += 2;
        }
        if (profile.has(StatFlag.HEAVY_METAL_ERRATA)) {
            base = Math.max(1, base - 2);
        }
        if (profile.has(StatFlag.LIGHT_METAL_ERRATA)) {
            base = Math.max(1, base + 2);
        }
        if (profile.has(StatFlag.PARALYZED) && !quickFeetActive) {
            stage -= 4;
        }
        return apply(profile, stat, base, stage, false);
    }

    private static int rawBaseWithPhysicalDefenseMetal(
            CombatantStatProfile profile,
            CombatStat stat,
            boolean includeLightMetal
    ) {
        int base = profile.base(stat);
        if (stat == CombatStat.DEF && profile.has(StatFlag.HEAVY_METAL_ERRATA)) {
            base += 2;
        }
        if (includeLightMetal && stat == CombatStat.DEF && profile.has(StatFlag.LIGHT_METAL_ERRATA)) {
            base = Math.max(1, base - 2);
        }
        return base;
    }

    private static int apply(
            CombatantStatProfile profile,
            CombatStat stat,
            int rawBase,
            int stage,
            boolean ignorePositiveStage
    ) {
        StatModifier modifier = profile.modifier(stat);
        int base = Math.max(1, rawBase + modifier.additive());
        base = (int) Math.floor(base * modifier.scalar());
        if (ignorePositiveStage && stage > 0) {
            stage = 0;
        }
        return (int) Math.floor(base * Calculations.stageMultiplier(stage)) + modifier.postStageBonus();
    }

    private static String normalizeCategory(String category) {
        return category == null ? "special" : category.strip().toLowerCase(Locale.ROOT);
    }

    private static void requireProfile(CombatantStatProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required");
        }
    }
}
