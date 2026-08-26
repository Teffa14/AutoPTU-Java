package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EffectiveAccuracyStageProjectionTest {
    @Test
    void addsDynamicIntrinsicAndRuntimeAccuracyBeforeClamping() {
        assertEquals(3, EffectiveAccuracyStageProjection.resolve(1, 2, 0));
        assertEquals(6, EffectiveAccuracyStageProjection.resolve(4, 3, 2));
        assertEquals(-6, EffectiveAccuracyStageProjection.resolve(-4, -3, -2));
    }

    @Test
    void keepsTheThreeAccuracySourcesIndependentUntilProjection() {
        assertEquals(4, EffectiveAccuracyStageProjection.resolve(4, 0, 0));
        assertEquals(4, EffectiveAccuracyStageProjection.resolve(0, 4, 0));
        assertEquals(4, EffectiveAccuracyStageProjection.resolve(0, 0, 4));
    }
}
