package io.autoptu.core.hook;

import io.autoptu.core.runtime.TemporaryEffectEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuraBreakErrataAdjustmentTest {
    @Test
    void matchingLiveEffectInvertsPositiveBonusAndPreservesSource() {
        AuraBreakErrataAdjustment result = AuraBreakErrataAdjustment.resolve(
                "Aura Storm [Errata]",
                6,
                4,
                List.of(effect("Aura Storm [Errata]", "breaker", 4))
        );

        assertEquals(-6, result.adjustedBonus());
        assertTrue(result.emitAuraBreakEvent());
        assertEquals("breaker", result.sourceId());
        assertFalse(result.clearAuraBreakEffects());
    }

    @Test
    void abilityMismatchDoesNotInvert() {
        AuraBreakErrataAdjustment result = AuraBreakErrataAdjustment.resolve(
                "Analytic",
                5,
                4,
                List.of(effect("Aura Storm [Errata]", "breaker", 4))
        );

        assertEquals(5, result.adjustedBonus());
        assertFalse(result.emitAuraBreakEvent());
        assertFalse(result.clearAuraBreakEffects());
    }

    @Test
    void expiredEffectRequestsCanonicalCleanupAndDoesNotInvertItself() {
        AuraBreakErrataAdjustment result = AuraBreakErrataAdjustment.resolve(
                "Aura Storm [Errata]",
                6,
                5,
                List.of(effect("Aura Storm [Errata]", "breaker", 4))
        );

        assertEquals(6, result.adjustedBonus());
        assertFalse(result.emitAuraBreakEvent());
        assertTrue(result.clearAuraBreakEffects());
    }

    @Test
    void scanUsesOriginalSnapshotEvenAfterExpiredEntryRequestsCleanup() {
        AuraBreakErrataAdjustment result = AuraBreakErrataAdjustment.resolve(
                "Aura Storm [Errata]",
                6,
                5,
                List.of(
                        effect("Other Ability", "old-breaker", 4),
                        effect("Aura Storm [Errata]", "live-breaker", 5)
                )
        );

        assertEquals(-6, result.adjustedBonus());
        assertTrue(result.emitAuraBreakEvent());
        assertEquals("live-breaker", result.sourceId());
        assertTrue(result.clearAuraBreakEffects());
    }

    @Test
    void nonPositiveBonusReturnsBeforeExpiredEffectsAreScanned() {
        AuraBreakErrataAdjustment result = AuraBreakErrataAdjustment.resolve(
                "Aura Storm [Errata]",
                0,
                5,
                List.of(effect("Aura Storm [Errata]", "breaker", 4))
        );

        assertEquals(0, result.adjustedBonus());
        assertFalse(result.emitAuraBreakEvent());
        assertFalse(result.clearAuraBreakEffects());
    }

    @Test
    void resolvedInversionHelperMatchesExistingAuraStormOracleBoundary() {
        AuraBreakErrataAdjustment normal = AuraBreakErrataAdjustment.fromResolvedInversion(6, false);
        AuraBreakErrataAdjustment inverted = AuraBreakErrataAdjustment.fromResolvedInversion(6, true);

        assertEquals(6, normal.adjustedBonus());
        assertFalse(normal.emitAuraBreakEvent());
        assertEquals(-6, inverted.adjustedBonus());
        assertTrue(inverted.emitAuraBreakEvent());
    }

    private static TemporaryEffectEntry effect(String ability, String sourceId, int expiresRound) {
        return new TemporaryEffectEntry(
                "aura_break_errata",
                Map.of(
                        "ability", ability,
                        "source_id", sourceId,
                        "expires_round", expiresRound
                )
        );
    }
}
