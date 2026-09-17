package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;

import java.util.List;

/**
 * Appends reaction-relevant semantic occurrences after an authoritative move has completed.
 *
 * <p>This boundary deliberately runs after move resolution so reaction discovery observes the
 * targets that the core actually resolved. Adapters do not classify range or PTU reaction rules.</p>
 */
public final class RuntimeMoveReactionProjection {
    private RuntimeMoveReactionProjection() {
    }

    /**
     * Projects a completed single-target move into the ordered semantic event stream.
     * The occurrence is appended after the move's own hit/damage/status events.
     */
    public static AppliedActionResult appendCompletedMoveOccurrence(
            AppliedActionResult result,
            MoveChoice choice,
            MoveOption move
    ) {
        if (result == null) throw new IllegalArgumentException("result is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        return RuntimeMoveActionOccurrenceFactory
                .fromResolvedTargets(choice, move, List.of(choice.targetId()))
                .map(event -> result.appendEvents(List.of(event)))
                .orElse(result);
    }

    /**
     * Projects a completed authoritative target expansion into the ordered semantic event stream.
     * Target ids must be the resolved target order returned by the core.
     */
    public static AppliedActionResult appendCompletedMoveOccurrence(
            AppliedActionResult result,
            MoveChoice choice,
            MoveOption move,
            List<String> resolvedTargetIds
    ) {
        if (result == null) throw new IllegalArgumentException("result is required");
        return RuntimeMoveActionOccurrenceFactory
                .fromResolvedTargets(choice, move, resolvedTargetIds)
                .map(event -> result.appendEvents(List.of(event)))
                .orElse(result);
    }
}
