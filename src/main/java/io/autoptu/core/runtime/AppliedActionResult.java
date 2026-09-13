package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.rules.ActionSpendResult;

import java.util.List;
import java.util.Optional;

/** Ordered semantic output of one authoritative battle action. */
public record AppliedActionResult(List<BattleEvent> events, ActionSpendResult actionSpend) {
    public AppliedActionResult {
        events = List.copyOf(events == null ? List.of() : events);
    }

    /** Existing runtime callers remain source-compatible until spend provenance is wired in. */
    public AppliedActionResult(List<BattleEvent> events) {
        this(events, null);
    }

    /**
     * Optional authoritative action-economy provenance for this action application.
     * This metadata is deliberately outside the Python-compatible semantic event stream.
     */
    public Optional<ActionSpendResult> actionSpendResult() {
        return Optional.ofNullable(actionSpend);
    }
}
