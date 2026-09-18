package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.List;

/**
 * Ordered authoritative output from executing one already committed action-window instruction.
 *
 * <p>The result keeps reaction execution on the same event boundary as ordinary battle actions.
 * Handlers return the exact ordered events they produced; adapters render these events and do not
 * reconstruct PTU consequences.</p>
 */
public record ActionWindowExecutionResult(List<BattleEvent> events) {
    public ActionWindowExecutionResult {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static ActionWindowExecutionResult empty() {
        return new ActionWindowExecutionResult(List.of());
    }
}
