package io.autoptu.core.action;

import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.MoveSpec;

/** Resolved move metadata presented to legal-action generation. */
public record MoveOption(
        String moveId,
        MoveSpec spec,
        ActionType actionType,
        boolean requiresLineOfSight
) {
    public MoveOption {
        if (moveId == null || moveId.isBlank()) {
            throw new IllegalArgumentException("moveId is required");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec is required");
        }
        actionType = actionType == null ? ActionType.STANDARD : actionType;
    }

    public static MoveOption standard(String moveId, MoveSpec spec) {
        return new MoveOption(moveId, spec, ActionType.STANDARD, true);
    }
}
