package io.autoptu.core.rules;

import io.autoptu.core.model.AccuracyCheck;
import io.autoptu.core.model.AccuracyResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccuracyTest {
    @Test
    void naturalOneMissesNormalChecks() {
        AccuracyResult result = Accuracy.resolve(check(2, 0, 0, 1, null, 20, false, false));
        assertFalse(result.hit());
        assertFalse(result.crit());
        assertEquals(2, result.needed());
    }

    @Test
    void naturalTwentyHitsEvenWhenNeededExceedsTwenty() {
        AccuracyResult result = Accuracy.resolve(check(25, 0, 0, 20, null, 20, false, false));
        assertTrue(result.hit());
        assertTrue(result.crit());
        assertEquals(25, result.needed());
    }

    @Test
    void accuracyStageIsClampedBeforeNeededIsCalculated() {
        AccuracyResult result = Accuracy.resolve(check(10, 3, 9, 7, null, 20, false, false));
        assertTrue(result.hit());
        assertEquals(7, result.needed());
    }

    @Test
    void meleeNoGuardRemovesEvasionOnlyForNormalAcChecks() {
        AccuracyResult result = Accuracy.resolve(check(6, 5, 0, 6, null, 20, true, false));
        assertTrue(result.hit());
        assertEquals(6, result.needed());
    }

    @Test
    void acNoneIsAutomaticUnlessBlurApplies() {
        AccuracyResult automatic = Accuracy.resolve(check(null, 9, 0, 1, null, 20, false, false));
        assertTrue(automatic.hit());
        assertEquals(1, automatic.needed());

        AccuracyResult blurred = Accuracy.resolve(check(null, 7, 0, 5, null, 20, false, true));
        assertTrue(blurred.hit());
        assertEquals(5, blurred.needed());
    }

    @Test
    void probabilityControlRerollReplacesAFailedRoll() {
        AccuracyResult result = Accuracy.resolve(check(10, 0, 0, 1, 15, 20, false, false));
        assertTrue(result.hit());
        assertFalse(result.crit());
        assertEquals(15, result.roll());
        assertEquals(10, result.needed());
    }

    @Test
    void criticalThresholdOnlyMattersOnHits() {
        AccuracyResult miss = Accuracy.resolve(check(20, 0, 0, 18, null, 18, false, false));
        assertFalse(miss.hit());
        assertFalse(miss.crit());

        AccuracyResult hit = Accuracy.resolve(check(10, 0, 0, 18, null, 18, false, false));
        assertTrue(hit.hit());
        assertTrue(hit.crit());
    }

    @Test
    void hitProbabilityMatchesPtuNaturalRollBounds() {
        assertEquals(0.95, Accuracy.hitProbability(check(2, 0, 0, 10, null, 20, false, false)), 1e-12);
        assertEquals(0.05, Accuracy.hitProbability(check(25, 0, 0, 10, null, 20, false, false)), 1e-12);
        assertEquals(1.0, Accuracy.hitProbability(check(null, 10, 0, 10, null, 20, false, false)), 1e-12);
    }

    private static AccuracyCheck check(
            Integer ac,
            int evasion,
            int stage,
            int roll,
            Integer reroll,
            int critRange,
            boolean noGuard,
            boolean blur
    ) {
        return new AccuracyCheck(ac, evasion, stage, roll, reroll, critRange, noGuard, blur);
    }
}
