package io.autoptu.core.action;

import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;

/**
 * Legal move decision. targetId is blank for FIELD/TILE targets; targetAnchor is
 * always present so a renderer can aim the animation without knowing PTU rules.
 */
public record MoveChoice(
        String actorId,
        String moveId,
        ChoiceTargetMode targetMode,
        String targetId,
        GridCoord targetAnchor,
        ActionType actionType
) implements BattleChoice {
    public MoveChoice {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
        if (moveId == null || moveId.isBlank()) {
            throw new IllegalArgumentException("moveId is required");
        }
        if (targetMode == null) {
            throw new IllegalArgumentException("targetMode is required");
        }
        targetId = targetId == null ? "" : targetId;
        if (targetAnchor == null) {
            throw new IllegalArgumentException("targetAnchor is required");
        }
        actionType = actionType == null ? ActionType.STANDARD : actionType;
    }

    @Override
    public String stableKey() {
        return "move|" + actorId
                + "|" + moveId
                + "|" + targetMode.name().toLowerCase()
                + "|" + targetId
                + "|" + targetAnchor.x() + "," + targetAnchor.y()
                + "|" + actionType.value();
    }
}
