package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.List;

/** Ordered semantic events emitted by a synchronous PRE-damage follow-up move resolution. */
public record PreDamageFollowUpMoveResult(List<BattleEvent> events) {
    public PreDamageFollowUpMoveResult {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static PreDamageFollowUpMoveResult empty() {
        return new PreDamageFollowUpMoveResult(List.of());
    }
}
