package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

import java.util.List;

/** Authoritative result of beginning a battle round. */
public record RoundStartResult(int round, List<BattleEvent> events) {
    public RoundStartResult {
        if (round < 1) throw new IllegalArgumentException("round must be positive");
        events = events == null ? List.of() : List.copyOf(events);
    }
}
