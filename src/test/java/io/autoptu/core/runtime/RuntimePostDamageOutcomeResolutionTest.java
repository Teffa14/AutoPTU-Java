package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RuntimePostDamageOutcomeResolutionTest {
    @Test
    void reportsAliveToAliveWithoutFaintTransition() {
        assertEquals(
                new RuntimePostDamageOutcomeResolution.Result(false, false, false),
                RuntimePostDamageOutcomeResolution.resolve(20, 7)
        );
    }

    @Test
    void reportsAliveToZeroAsSingleFaintTransition() {
        assertEquals(
                new RuntimePostDamageOutcomeResolution.Result(false, true, true),
                RuntimePostDamageOutcomeResolution.resolve(7, 0)
        );
    }

    @Test
    void alreadyFaintedCombatantDoesNotInventAnotherTransition() {
        assertEquals(
                new RuntimePostDamageOutcomeResolution.Result(true, true, false),
                RuntimePostDamageOutcomeResolution.resolve(0, 0)
        );
    }

    @Test
    void rejectsHealingAndNegativeHpAtDamageBoundary() {
        assertThrows(IllegalArgumentException.class, () -> RuntimePostDamageOutcomeResolution.resolve(5, 6));
        assertThrows(IllegalArgumentException.class, () -> RuntimePostDamageOutcomeResolution.resolve(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> RuntimePostDamageOutcomeResolution.resolve(1, -1));
    }
}
