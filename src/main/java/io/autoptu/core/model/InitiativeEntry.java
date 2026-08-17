package io.autoptu.core.model;

/** Stored initiative-order details mirrored from Python battle_state.InitiativeEntry. */
public record InitiativeEntry(
        String actorId,
        String trainerId,
        int speed,
        int trainerModifier,
        int roll,
        int total
) {
    public InitiativeEntry {
        actorId = actorId == null ? "" : actorId;
        trainerId = trainerId == null ? "" : trainerId;
    }
}
