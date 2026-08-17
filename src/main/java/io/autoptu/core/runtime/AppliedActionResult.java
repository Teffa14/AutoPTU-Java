package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

import java.util.List;

/** Ordered semantic output of one authoritative battle action. */
public record AppliedActionResult(List<BattleEvent> events) {
    public AppliedActionResult {
        events = List.copyOf(events == null ? List.of() : events);
    }
}
