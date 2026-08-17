package io.autoptu.core.rules;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.StatFlag;
import io.autoptu.core.model.StatModifier;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatResolutionTest {
    @Test
    void physicalOffenseUsesAttackAndStages() {
        CombatantStatProfile profile = profile(Map.of(CombatStat.ATK, 12), Map.of(CombatStat.ATK, 2), Map.of(), Set.of());
        assertEquals(24, StatResolution.offensive(profile, "Physical", false));
    }

    @Test
    void powerShiftPhysicalOffenseUsesDefense() {
        CombatantStatProfile profile = profile(
                Map.of(CombatStat.ATK, 5, CombatStat.DEF, 20),
                Map.of(CombatStat.DEF, 1), Map.of(), Set.of(StatFlag.POWER_SHIFT)
        );
        assertEquals(30, StatResolution.offensive(profile, "Physical", false));
    }

    @Test
    void statModifiersPreservePythonFloorOrder() {
        CombatantStatProfile profile = profile(
                Map.of(CombatStat.ATK, 10), Map.of(CombatStat.ATK, 1),
                Map.of(CombatStat.ATK, new StatModifier(3, 1.5, 2)), Set.of()
        );
        assertEquals(30, StatResolution.offensive(profile, "Physical", false));
    }

    @Test
    void burnReducesPhysicalDefenseStageByTwo() {
        CombatantStatProfile profile = profile(Map.of(CombatStat.DEF, 18), Map.of(), Map.of(), Set.of(StatFlag.BURNED));
        assertEquals(9, StatResolution.defensive(profile, "Physical", false));
    }

    @Test
    void wonderRoomUsesSpecialDefenseForPhysicalDefense() {
        CombatantStatProfile profile = profile(
                Map.of(CombatStat.DEF, 7, CombatStat.SPDEF, 22), Map.of(), Map.of(), Set.of(StatFlag.WONDER_ROOM)
        );
        assertEquals(22, StatResolution.defensive(profile, "Physical", false));
    }

    @Test
    void poisonReducesSpecialDefenseStageUnlessOverridden() {
        CombatantStatProfile poisoned = profile(Map.of(CombatStat.SPDEF, 20), Map.of(), Map.of(), Set.of(StatFlag.POISONED));
        CombatantStatProfile protectedProfile = profile(
                Map.of(CombatStat.SPDEF, 20), Map.of(), Map.of(),
                Set.of(StatFlag.POISONED, StatFlag.POTENT_VENOM_OVERRIDE)
        );
        assertEquals(10, StatResolution.defensive(poisoned, "Special", false));
        assertEquals(20, StatResolution.defensive(protectedProfile, "Special", false));
    }

    @Test
    void quickFeetAddsTwoStagesAndSuppressesParalysisPenalty() {
        CombatantStatProfile profile = profile(
                Map.of(CombatStat.SPD, 16), Map.of(), Map.of(),
                Set.of(StatFlag.QUICK_FEET, StatFlag.MAJOR_STATUS, StatFlag.PARALYZED)
        );
        assertEquals(32, StatResolution.speed(profile));
    }

    @Test
    void paralysisAloneRemovesFourSpeedStages() {
        CombatantStatProfile profile = profile(Map.of(CombatStat.SPD, 18), Map.of(), Map.of(), Set.of(StatFlag.PARALYZED));
        assertEquals(6, StatResolution.speed(profile));
    }

    private static CombatantStatProfile profile(
            Map<CombatStat, Integer> bases,
            Map<CombatStat, Integer> stages,
            Map<CombatStat, StatModifier> modifiers,
            Set<StatFlag> flags
    ) {
        EnumMap<CombatStat, Integer> defaults = new EnumMap<>(CombatStat.class);
        for (CombatStat stat : CombatStat.values()) defaults.put(stat, 10);
        defaults.putAll(bases);
        return new CombatantStatProfile(defaults, stages, modifiers, flags);
    }
}
