package io.autoptu.core.event;

import io.autoptu.core.model.TurnPhase;

/**
 * Semantic notification that a status prevented the combatant's normal turn.
 * Minecraft should render this event without deciding which actions were lost.
 */
public record StatusSkipEvent(
        String actorId,
        String status,
        TurnPhase phase,
        String reason
) implements BattleEvent {
    public StatusSkipEvent {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
        actorId = actorId.strip();
        status = status == null ? "" : status.strip();
        if (phase == null) {
            throw new IllegalArgumentException("phase is required");
        }
        reason = reason == null ? "" : reason.strip();
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.STATUS_SKIP;
    }

    @Override
    public String stableKey() {
        return kind().value()
                + "|" + actorId
                + "|" + status
                + "|" + phase.value()
                + "|" + reason;
    }
}
