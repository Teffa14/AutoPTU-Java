package io.autoptu.core.rules;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
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
    void unrelatedStatusesDoNotSuppressEvasion() {
        EvasionProfile profile = StatusEvasionResolution.apply(profile(3), Set.of("Burned"));
        assertFalse(profile.suppressPositiveBonuses());
    }

    private static EvasionProfile profile(int bonus) {
        CombatantStatProfile stats = new CombatantStatProfile(
                Map.of(CombatStat.DEF, 1), Map.of(), Map.of(), Set.of());
        return new EvasionProfile(stats, bonus, 0, 0, false, false);
    }
}
