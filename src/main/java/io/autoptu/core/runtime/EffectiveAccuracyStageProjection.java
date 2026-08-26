package io.autoptu.core.runtime;

import io.autoptu.core.rules.Calculations;

/**
 * Projects Python's effective Accuracy Combat Stage from its three additive
 * server-owned inputs before PTU's -6..+6 clamp is applied.
 *
 * <p>The inputs deliberately remain separate: the mutable Accuracy Combat Stage,
 * the Pokemon-spec intrinsic Accuracy CS, and runtime bonuses are different
 * sources in the Python oracle and must not be collapsed at persistence or
 * adapter boundaries.</p>
 */
final class EffectiveAccuracyStageProjection {
    private EffectiveAccuracyStageProjection() {
    }

    static int resolve(int dynamicStage, int intrinsicAccuracyCs, int runtimeBonus) {
        return Calculations.accuracyStageValue(dynamicStage + intrinsicAccuracyCs + runtimeBonus);
    }
}
