package io.autoptu.core.hook;

/** Language-neutral payload matching Python's should_trigger_out_of_turn contract. */
public record OutOfTurnDecisionRequest(
        String actorId,
        String label,
        String phase,
        String moveName,
        String triggerMoveName,
        String attackerId,
        String defenderId,
        boolean optional
) {
    public OutOfTurnDecisionRequest {
        actorId = require(actorId, "actorId");
        label = require(label, "label");
        phase = require(phase, "phase");
        moveName = normalize(moveName);
        triggerMoveName = normalize(triggerMoveName);
        attackerId = normalize(attackerId);
        defenderId = normalize(defenderId);
    }

    public static OutOfTurnDecisionRequest preDamageInterrupt(
            String actorId,
            String label,
            String moveName,
            String attackerId,
            String defenderId,
            boolean optional
    ) {
        return new OutOfTurnDecisionRequest(
                actorId, label, "pre_damage_interrupt", moveName, moveName,
                attackerId, defenderId, optional
        );
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.strip();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
