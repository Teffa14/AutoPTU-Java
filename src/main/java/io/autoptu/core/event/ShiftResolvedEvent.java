package io.autoptu.core.event;

import io.autoptu.core.model.GridCoord;

/**
 * Authoritative movement result for one combatant.
 *
 * Minecraft/Craftics may animate any path between these anchors, but the core owns
 * the resolved origin and destination.
 */
public record ShiftResolvedEvent(
        String actorId,
        GridCoord origin,
        GridCoord destination
) implements BattleEvent {
    public ShiftResolvedEvent {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
        actorId = actorId.strip();
        if (origin == null) {
            throw new IllegalArgumentException("origin is required");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination is required");
        }
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("shift destination must differ from origin");
        }
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.SHIFT_RESOLVED;
    }

    @Override
    public String stableKey() {
        return kind().value()
                + "|" + actorId
                + "|" + origin.x() + "," + origin.y()
                + "|" + destination.x() + "," + destination.y();
    }
}
