package io.autoptu.core.event;

import io.autoptu.core.model.TurnPhase;

import java.util.Objects;

/** Semantic playback event emitted when initiative opens an authoritative combatant turn. */
public record TurnStartedEvent(
        String actorId,
        int round,
        TurnPhase phase,
        int initiativeIndex
) implements BattleEvent {
    public TurnStartedEvent {
        actorId = actorId == null ? "" : actorId.strip();
        if (actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
        phase = Objects.requireNonNull(phase, "phase");
        if (initiativeIndex < 0) throw new IllegalArgumentException("initiativeIndex cannot be negative");
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.TURN_START;
    }

    @Override
    public String stableKey() {
        return String.join("|",
                kind().value(),
                Integer.toString(round),
                actorId,
                phase.value(),
                Integer.toString(initiativeIndex)
        );
    }
}
