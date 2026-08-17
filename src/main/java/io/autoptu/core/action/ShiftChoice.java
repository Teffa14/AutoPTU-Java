package io.autoptu.core.action;

import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;

/** Legal PTU Shift destination. Minecraft may animate the route after this choice is accepted. */
public record ShiftChoice(String actorId, GridCoord destination) implements BattleChoice {
    public ShiftChoice {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination is required");
        }
    }

    @Override
    public ActionType actionType() {
        return ActionType.SHIFT;
    }

    @Override
    public String stableKey() {
        return "shift|" + actorId + "|" + destination.x() + "," + destination.y();
    }
}
