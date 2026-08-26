package io.autoptu.core.runtime;

import io.autoptu.core.rules.FocusedTrainingAccuracyBonusResolution;

/**
 * Materializes Python BattleState._focused_training_accuracy_bonus inputs from canonical battle state.
 *
 * Duelist tags and momentum belong to Pokemon temporary effects in the pinned oracle. The runtime
 * therefore scans combatants controlled by the same Trainer instead of introducing a second Trainer-
 * owned tag store or accepting a precomputed bonus from Minecraft/Cobblemon.
 */
final class FocusedTrainingAccuracyRuntimeInputs {
    private FocusedTrainingAccuracyRuntimeInputs() {
    }

    static FocusedTrainingAccuracyBonusResolution.Input fromState(
            BattleRuntimeState state,
            String attackerId,
            String defenderId
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        RuntimeCombatantState attacker = state.requireCombatant(attackerId);
        state.requireCombatant(defenderId);

        boolean focusedTraining = attacker.temporaryEffects().has("focused_training");
        if (!focusedTraining || !state.hasCanonicalTrainer(attackerId)) {
            return new FocusedTrainingAccuracyBonusResolution.Input(
                    focusedTraining, false, false, false, 0);
        }

        String controllerId = state.controllerId(attackerId);
        boolean duelist = state.requireTrainer(controllerId).hasTrainerFeature("Duelist");
        boolean anyTag = false;
        boolean defenderTagged = false;
        int momentum = 0;

        for (String combatantId : state.combatantIds()) {
            if (!state.hasCanonicalTrainer(combatantId)) continue;
            if (!state.controllerId(combatantId).equals(controllerId)) continue;

            RuntimeCombatantState controlled = state.requireCombatant(combatantId);
            for (TemporaryEffectEntry entry : controlled.temporaryEffects().getAll("duelist_tag")) {
                Object tagged = entry.payload().get("tagged");
                if (pythonTruthy(tagged)) {
                    anyTag = true;
                    String taggedTarget = pythonString(firstTruthy(
                            entry.payload().get("target_id"),
                            entry.payload().get("target"),
                            combatantId
                    )).strip();
                    if (taggedTarget.equals(defenderId)) defenderTagged = true;
                }

                Integer value = pythonInt(entry.payload().get("momentum"));
                if (value != null) momentum = Math.max(momentum, value);
            }
        }

        return new FocusedTrainingAccuracyBonusResolution.Input(
                true,
                duelist,
                anyTag,
                defenderTagged,
                Math.max(0, momentum)
        );
    }

    static int resolve(BattleRuntimeState state, String attackerId, String defenderId) {
        return FocusedTrainingAccuracyBonusResolution.resolve(fromState(state, attackerId, defenderId));
    }

    private static Object firstTruthy(Object first, Object second, Object fallback) {
        if (pythonTruthy(first)) return first;
        if (pythonTruthy(second)) return second;
        return fallback;
    }

    private static boolean pythonTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.doubleValue() != 0.0d;
        if (value instanceof String text) return !text.isEmpty();
        return true;
    }

    private static Integer pythonInt(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean flag) return flag ? 1 : 0;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String pythonString(Object value) {
        if (value == null) return "None";
        if (value instanceof Boolean flag) return flag ? "True" : "False";
        return String.valueOf(value);
    }
}
