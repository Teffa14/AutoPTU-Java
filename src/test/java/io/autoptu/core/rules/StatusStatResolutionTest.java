package io.autoptu.core.rules;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.StatFlag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusStatResolutionTest {
    @Test
    void canonicalBurnDrivesPhysicalDefensePenalty() {
        CombatantStatProfile resolved = StatusStatResolution.apply(profile(Set.of()), Set.of("Burned"));

        assertTrue(resolved.has(StatFlag.BURNED));
        assertEquals(9, StatResolution.defensive(resolved, "Physical", false));
    }

    @Test
    void canonicalPoisonDrivesSpecialDefensePenalty() {
        CombatantStatProfile resolved = StatusStatResolution.apply(profile(Set.of()), Set.of("Poisoned"));

        assertTrue(resolved.has(StatFlag.POISONED));
        assertEquals(10, StatResolution.defensive(resolved, "Special", false));
    }

    @Test
    void canonicalParalysisDrivesSpeedPenalty() {
        CombatantStatProfile resolved = StatusStatResolution.apply(profile(Set.of()), Set.of("Paralyzed"));

        assertTrue(resolved.has(StatFlag.PARALYZED));
        assertTrue(resolved.has(StatFlag.MAJOR_STATUS));
        assertEquals(6, StatResolution.speed(resolved));
    }

    @Test
    void canonicalMajorStatusActivatesQuickFeetAndSuppressesParalysisPenalty() {
        CombatantStatProfile resolved = StatusStatResolution.apply(
                profile(Set.of(StatFlag.QUICK_FEET)),
                Set.of("Paralyzed")
        );

        assertEquals(36, StatResolution.speed(resolved));
    }

    @Test
    void canonicalStateClearsStaleStatusFlags() {
        CombatantStatProfile resolved = StatusStatResolution.apply(
                profile(Set.of(StatFlag.BURNED, StatFlag.POISONED, StatFlag.PARALYZED, StatFlag.MAJOR_STATUS)),
                Set.of()
        );

        assertFalse(resolved.has(StatFlag.BURNED));
        assertFalse(resolved.has(StatFlag.POISONED));
        assertFalse(resolved.has(StatFlag.PARALYZED));
        assertFalse(resolved.has(StatFlag.MAJOR_STATUS));
        assertEquals(18, StatResolution.defensive(resolved, "Physical", false));
        assertEquals(20, StatResolution.defensive(resolved, "Special", false));
        assertEquals(18, StatResolution.speed(resolved));
    }

    private static CombatantStatProfile profile(Set<StatFlag> flags) {
        return new CombatantStatProfile(
                Map.of(
                        CombatStat.ATK, 12,
                        CombatStat.DEF, 18,
                        CombatStat.SPATK, 12,
                        CombatStat.SPDEF, 20,
                        CombatStat.SPD, 18
                ),
                Map.of(),
                Map.of(),
                flags
        );
    }
}
