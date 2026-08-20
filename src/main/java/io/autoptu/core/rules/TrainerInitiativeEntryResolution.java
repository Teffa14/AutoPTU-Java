package io.autoptu.core.rules;

import io.autoptu.core.model.InitiativeEntry;

/**
 * Pure parity boundary for the Trainer entry built by Python BattleState._build_initiative_order().
 *
 * The trainer's initiative Speed and initiative bonus are already authoritative rule inputs.
 * Tailwind contributes a fixed +5 when the trainer's team (or identifier fallback) is affected.
 */
public final class TrainerInitiativeEntryResolution {
    private TrainerInitiativeEntryResolution() {
    }

    public static InitiativeEntry resolve(
            String trainerId,
            int speed,
            int initiativeBonus,
            boolean tailwindActive
    ) {
        if (trainerId == null || trainerId.isBlank()) {
            throw new IllegalArgumentException("trainerId is required");
        }
        String canonicalTrainerId = trainerId.strip();
        int total = speed + initiativeBonus + (tailwindActive ? 5 : 0);
        return new InitiativeEntry(
                canonicalTrainerId,
                canonicalTrainerId,
                speed,
                initiativeBonus,
                0,
                total
        );
    }
}
