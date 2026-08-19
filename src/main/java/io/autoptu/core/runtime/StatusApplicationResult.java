package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

import java.util.List;

public record StatusApplicationResult(boolean applied, StatusEntry status, List<BattleEvent> events) {
    public StatusApplicationResult {
        if (status == null) throw new IllegalArgumentException("status is required");
        events = events == null ? List.of() : List.copyOf(events);
    }
}
