package io.autoptu.core.rules;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.StatFlag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusEvasionResolutionTest {
    @Test
    void sleepSuppressesOnlyPositiveNonStatEvasion() {
        EvasionProfile positive = profile(3);
        EvasionProfile negative = profile(-2);

        EvasionProfile sleepingPositive = StatusEvasionResolution.apply(positive, Set.of("Sleep"));
        EvasionProfile sleepingNegative = StatusEvasionResolution.apply(negative, Set.of("asleep"));

        assertTrue(sleepingPositive.suppressPositiveBonuses());
        assertTrue(sleepingNegative.suppressPositiveBonuses());
        assertEquals(3, EvasionResolution.resolve(positive, "Physical"));
        assertEquals(0, EvasionResolution.resolve(sleepingPositive, "Physical"));
        assertEquals(-2, EvasionResolution.resolve(sleepingNegative, "Physical"));
    }

    @Test
    void paralysisStatusDrivesStatusEvasionPenalty() {
        EvasionProfile clean = statusProfile(Set.of());
        EvasionProfile paralyzed = StatusEvasionResolution.apply(clean, Set.of("Paralyzed"));
        EvasionProfile alias = StatusEvasionResolution.apply(clean, Set.of("paralyze"));

        assertFalse(clean.stats().has(StatFlag.PARALYZED));
        assertTrue(paralyzed.stats().has(StatFlag.PARALYZED));
        assertTrue(alias.stats().has(StatFlag.PARALYZED));
        assertEquals(6, EvasionResolution.resolve(clean, "Status"));
        assertEquals(2, EvasionResolution.resolve(paralyzed, "Status"));
        assertEquals(2, EvasionResolution.resolve(alias, "Status"));
    }

    @Test
    void canonicalStatusesClearStaleParalysisFlag() {
        EvasionProfile stale = statusProfile(Set.of(StatFlag.PARALYZED));
        EvasionProfile canonical = StatusEvasionResolution.apply(stale, Set.of());

        assertTrue(stale.stats().has(StatFlag.PARALYZED));
        assertFalse(canonical.stats().has(StatFlag.PARALYZED));
        assertEquals(2, EvasionResolution.resolve(stale, "Status"));
        assertEquals(6, EvasionResolution.resolve(canonical, "Status"));
    }

    @Test
    void unrelatedStatusesDoNotSuppressEvasion() {
        EvasionProfile profile = StatusEvasionResolution.apply(profile(3), Set.of("Burned"));
        assertFalse(profile.suppressPositiveBonuses());
    }

    private static EvasionProfile profile(int bonus) {
        CombatantStatProfile stats = new CombatantStatProfile(
                Map.of(CombatStat.DEF, 1), Map.of(), Map.of(), Set.of());
        return new EvasionProfile(stats, bonus, 0, 0, false, false);
    }

    private static EvasionProfile statusProfile(Set<StatFlag> flags) {
        CombatantStatProfile stats = new CombatantStatProfile(
                Map.of(CombatStat.SPD, 19),
                Map.of(CombatStat.SPD, 2),
                Map.of(),
                flags
        );
        return new EvasionProfile(stats, 0, 0, 1, false, false);
    }
}
