package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

import java.util.List;

/**
 * Ordered outcome of one declared multi-target move.
 *
 * <p>Target order is the authoritative expansion order frozen against the Python oracle.
 * HP and other mutable results remain canonical in {@link BattleRuntimeState}; this record
 * carries the ordered semantic events plus the identities that were actually resolved.</p>
 */
public record MultiTargetAppliedActionResult(
        List<BattleEvent> events,
        List<String> targetIds
) {
    public MultiTargetAppliedActionResult {
        events = events == null ? List.of() : List.copyOf(events);
        targetIds = targetIds == null ? List.of() : List.copyOf(targetIds);
    }
}
