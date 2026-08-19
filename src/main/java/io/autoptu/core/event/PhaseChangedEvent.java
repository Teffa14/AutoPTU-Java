package io.autoptu.core.event;

import io.autoptu.core.model.TurnPhase;

import java.util.Objects;

/** Semantic playback event emitted after the authoritative core advances a turn phase. */
public record PhaseChangedEvent(
        String actorId,
        int round,
        TurnPhase phase
) implements BattleEvent {
    public PhaseChangedEvent {
        actorId = actorId == null ? "" : actorId.strip();
        if (actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
        phase = Objects.requireNonNull(phase, "phase");
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.PHASE_CHANGE;
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
