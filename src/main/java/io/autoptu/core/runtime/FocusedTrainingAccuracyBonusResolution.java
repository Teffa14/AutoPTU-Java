package io.autoptu.core.runtime;

/**
 * Pure contract for the pinned Python BattleState._focused_training_accuracy_bonus() helper.
 *
 * <p>The resolver keeps Duelist ownership/tag state explicit so a runtime adapter can derive
 * those inputs from canonical battle state without moving PTU rules into Minecraft/Cobblemon.</p>
 */
final class FocusedTrainingAccuracyBonusResolution {
    private FocusedTrainingAccuracyBonusResolution() {
    }

    static int resolve(Input input) {
        if (input == null) throw new IllegalArgumentException("input is required");
        if (!input.focusedTrainingActive()) return 0;

        if (input.duelistFeature() && input.anyControllerTag()) {
            if (!input.defenderTagged()) return 0;
            return Math.max(0, (int) Math.ceil(input.momentum() / 2.0));
        }

        return 1;
    }

    record Input(
            boolean focusedTrainingActive,
            boolean duelistFeature,
            boolean anyControllerTag,
            boolean defenderTagged,
            int momentum
    ) {
    }
}
