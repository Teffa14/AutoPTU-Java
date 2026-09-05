package io.autoptu.core.rules;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.StatFlag;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvasionResolutionRulebookConformanceTest {
    @Test
    void physicalEvasionUsesDefenseAfterCombatStages() {
        CombatantStatProfile stats = profile(17, 10, 10, 2, 0, 0, Set.of());
        // PTU: floor(17 * 1.4) = 23 Defense, then floor(23 / 5) = 4 Evasion.
        assertEquals(4, resolve(stats, 0, 0, 0, "Physical"));
    }

    @Test
    void specialEvasionUsesSpecialDefenseAfterNegativeCombatStages() {
        CombatantStatProfile stats = profile(10, 24, 10, 0, -2, 0, Set.of());
        // PTU: floor(24 * 0.8) = 19 SpDef, then floor(19 / 5) = 3 Evasion.
        assertEquals(3, resolve(stats, 0, 0, 0, "Special"));
    }

    @Test
    void speedEvasionIsRecomputedFromStagedSpeedRatherThanAddingRawStage() {
        CombatantStatProfile stats = profile(10, 10, 19, 0, 0, -1, Set.of());
        // PTU: floor(19 * 0.9) = 17 Speed, then floor(17 / 5) = 3 Evasion.
        // The old Python-derived shortcut produced floor(19/5) - 1 = 2.
        assertEquals(3, resolve(stats, 0, 0, 0, "Status"));
    }

    @Test
    void statDerivedEvasionIsCappedAtSix() {
        CombatantStatProfile stats = profile(40, 10, 10, 0, 0, 0, Set.of());
        assertEquals(6, resolve(stats, 0, 0, 0, "Physical"));
    }

    @Test
    void finalPositiveEvasionContributionIsCappedAtNine() {
        CombatantStatProfile stats = profile(30, 10, 10, 0, 0, 0, Set.of());
        assertEquals(9, resolve(stats, 5, 0, 0, "Physical"));
    }

    @Test
    void negativeNonStatEvasionCanEraseButNeverImproveAccuracyBeyondBaseAc() {
        CombatantStatProfile stats = profile(17, 10, 10, 0, 0, 0, Set.of());
        assertEquals(0, resolve(stats, -8, 0, 0, "Physical"));
    }

    @Test
    void physicalEvasionSharesBurnDefenseProjectionWithDamageDefense() {
        CombatantStatProfile stats = profile(25, 10, 10, 0, 0, 0, Set.of(StatFlag.BURNED));
        assertEquals(4, resolve(stats, 0, 0, 0, "Physical"));
    }

    @Test
    void specialEvasionSharesPoisonSpecialDefenseProjectionWithDamageDefense() {
        CombatantStatProfile stats = profile(10, 25, 10, 0, 0, 0, Set.of(StatFlag.POISONED));
        assertEquals(4, resolve(stats, 0, 0, 0, "Special"));
    }

    private static int resolve(
            CombatantStatProfile stats,
            int physicalBonus,
            int specialBonus,
            int statusBonus,
            String category
    ) {
        return EvasionResolution.resolve(
                new EvasionProfile(stats, physicalBonus, specialBonus, statusBonus, false, false),
                category
        );
    }

    private static CombatantStatProfile profile(
            int defense,
            int specialDefense,
            int speed,
            int defenseStage,
            int specialDefenseStage,
            int speedStage,
            Set<StatFlag> flags
    ) {
        EnumMap<CombatStat, Integer> bases = new EnumMap<>(CombatStat.class);
        bases.put(CombatStat.DEF, defense);
        bases.put(CombatStat.SPDEF, specialDefense);
        bases.put(CombatStat.SPD, speed);

        EnumMap<CombatStat, Integer> stages = new EnumMap<>(CombatStat.class);
        stages.put(CombatStat.DEF, defenseStage);
        stages.put(CombatStat.SPDEF, specialDefenseStage);
        stages.put(CombatStat.SPD, speedStage);

        return new CombatantStatProfile(bases, stages, Map.of(), flags);
    }
}
