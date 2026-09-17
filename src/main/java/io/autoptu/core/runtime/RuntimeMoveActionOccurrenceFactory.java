package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.ActionResolvedEvent;

import java.util.List;
import java.util.Optional;

/**
 * Projects completed authoritative move declarations into semantic action occurrences.
 *
 * <p>Reaction discovery consumes these occurrences. Adapters must not classify move range or
 * decide whether a PTU reaction is legal.</p>
 */
public final class RuntimeMoveActionOccurrenceFactory {
    private RuntimeMoveActionOccurrenceFactory() {
    }

    /**
     * Returns the reaction-relevant occurrence for a completed move, when the move belongs to a
     * currently modeled occurrence family. Target ids must be the authoritative resolved targets,
     * in resolution order.
     */
    public static Optional<ActionResolvedEvent> fromResolvedTargets(
            MoveChoice choice,
            MoveOption move,
            List<String> resolvedTargetIds
    ) {
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (resolvedTargetIds == null) throw new IllegalArgumentException("resolvedTargetIds are required");
        if (!choice.actorId().equals(choice.actorId().strip())) {
            throw new IllegalArgumentException("choice actorId must be canonical");
        }
        if (!move.spec().hasRangeKeyword("ranged")) {
            return Optional.empty();
        }
        return Optional.of(ActionResolvedEvent.targeted(choice.actorId(), "ranged_attack", resolvedTargetIds));
    }
}
