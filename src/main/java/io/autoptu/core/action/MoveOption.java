package io.autoptu.core.action;

import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;

/** Resolved move metadata presented to legal-action generation and runtime resolution. */
public record MoveOption(
        String moveId,
        MoveSpec spec,
        ActionType actionType,
        boolean requiresLineOfSight,
        MoveCombatProfile combatProfile
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

    public MoveOption(
            String moveId,
            MoveSpec spec,
            ActionType actionType,
            boolean requiresLineOfSight
    ) {
        this(moveId, spec, actionType, requiresLineOfSight, null);
    }

    public static MoveOption standard(String moveId, MoveSpec spec) {
        return new MoveOption(moveId, spec, ActionType.STANDARD, true, null);
    }

    public static MoveOption standard(String moveId, MoveSpec spec, MoveCombatProfile combatProfile) {
        return new MoveOption(moveId, spec, ActionType.STANDARD, true, combatProfile);
    }

    public MoveCombatProfile requireCombatProfile() {
        if (combatProfile == null) {
            throw new IllegalStateException("move " + moveId + " has no combat profile");
        }
        return combatProfile;
    }
}
