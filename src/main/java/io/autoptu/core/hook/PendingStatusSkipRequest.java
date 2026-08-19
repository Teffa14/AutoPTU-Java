package io.autoptu.core.hook;

import io.autoptu.core.model.TurnPhase;

import java.util.Objects;

/**
 * Server-side request emitted by a phase hook when Python would populate
 * BattleState._pending_status_skip.
 *
 * The actor is intentionally not part of this value: BattleRoundController
 * always applies the request to its authoritative current actor.
 */
public record PendingStatusSkipRequest(
        String status,
        TurnPhase phase,
        String reason
) {
    public PendingStatusSkipRequest {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        status = status.strip();
        phase = Objects.requireNonNull(phase, "phase");
        reason = reason == null ? "" : reason.strip();
    }
}
