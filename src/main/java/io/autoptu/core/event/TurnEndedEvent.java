package io.autoptu.core.event;

import io.autoptu.core.model.TurnPhase;

import java.util.Objects;

/** Semantic playback event emitted when the authoritative core closes a turn. */
public record TurnEndedEvent(
        String actorId,
        int round,
        TurnPhase phase
) implements BattleEvent {
    public TurnEndedEvent {
        actorId = actorId == null ? "" : actorId.strip();
        if (actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
        phase = Objects.requireNonNull(phase, "phase");
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.TURN_END;
    }

    @Override
    public String stableKey() {
        return String.join("|",
                kind().value(),
                Integer.toString(round),
                actorId,
                phase.value()
        );
    }
}
