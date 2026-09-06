package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemporaryHpDamageAbsorptionTest {
    @Test
    void partiallyConsumesTemporaryHpBeforeOrdinaryDamageReachesHp() {
        TemporaryHpDamageAbsorption.Result result = TemporaryHpDamageAbsorption.resolve(7, 4);

        assertEquals(4, result.pendingDamage());
        assertEquals(4, result.absorbedDamage());
        assertEquals(0, result.remainingDamage());
        assertEquals(3, result.remainingTemporaryHp());
    }

    @Test
    void consumesAllTemporaryHpAndPreservesUnabsorbedDamageForNextIngressStage() {
        TemporaryHpDamageAbsorption.Result result = TemporaryHpDamageAbsorption.resolve(7, 12);

        assertEquals(12, result.pendingDamage());
        assertEquals(7, result.absorbedDamage());
        assertEquals(5, result.remainingDamage());
        assertEquals(0, result.remainingTemporaryHp());
    }

    @Test
    void zeroTemporaryHpLeavesPendingDamageUnchanged() {
        TemporaryHpDamageAbsorption.Result result = TemporaryHpDamageAbsorption.resolve(0, 9);

        assertEquals(9, result.pendingDamage());
        assertEquals(0, result.absorbedDamage());
        assertEquals(9, result.remainingDamage());
        assertEquals(0, result.remainingTemporaryHp());
    }

    @Test
    void nonPositiveIncomingDamageMatchesPythonPendingClamp() {
        TemporaryHpDamageAbsorption.Result zero = TemporaryHpDamageAbsorption.resolve(6, 0);
        TemporaryHpDamageAbsorption.Result negative = TemporaryHpDamageAbsorption.resolve(6, -3);

        assertEquals(new TemporaryHpDamageAbsorption.Result(0, 0, 0, 6), zero);
        assertEquals(new TemporaryHpDamageAbsorption.Result(0, 0, 0, 6), negative);
    }

    @Test
    void rejectsImpossibleNegativeTemporaryHp() {
        assertThrows(IllegalArgumentException.class, () -> TemporaryHpDamageAbsorption.resolve(-1, 4));
    }
}
