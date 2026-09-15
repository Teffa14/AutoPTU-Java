package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEventOccurrence;

import java.util.List;
import java.util.Objects;

/**
 * One authoritative action result paired with the battle-local identities assigned to
 * its semantic events at the runtime boundary.
 */
public record RecordedActionResult(
        AppliedActionResult result,
        List<BattleEventOccurrence> occurrences) {
    public RecordedActionResult {
        result = Objects.requireNonNull(result, "result");
        occurrences = List.copyOf(Objects.requireNonNull(occurrences, "occurrences"));
        if (occurrences.size() != result.events().size()) {
            throw new IllegalArgumentException("occurrence count must match semantic event count");
        }
    }
}
