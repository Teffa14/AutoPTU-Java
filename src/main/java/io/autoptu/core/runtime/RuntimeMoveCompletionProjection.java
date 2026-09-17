package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;

/**
 * Canonical post-resolution boundary for reaction-relevant move occurrences.
 *
 * <p>Both direct and area move runtimes can finish through this boundary without asking an
 * adapter to classify PTU range semantics or reconstruct the targets that actually resolved.</p>
 */
public final class RuntimeMoveCompletionProjection {
    private RuntimeMoveCompletionProjection() {
    }

    public static AppliedActionResult direct(
            AppliedActionResult result,
            MoveChoice choice,
            MoveOption move
    ) {
        return RuntimeMoveReactionProjection.appendCompletedMoveOccurrence(result, choice, move);
    }

    public static MultiTargetAppliedActionResult area(
            MultiTargetAppliedActionResult result,
            MoveChoice tileChoice,
            MoveOption move
    ) {
        if (result == null) throw new IllegalArgumentException("result is required");
        if (tileChoice == null) throw new IllegalArgumentException("tileChoice is required");
        if (move == null) throw new IllegalArgumentException("move is required");

        return RuntimeMoveActionOccurrenceFactory
                .fromResolvedTargets(tileChoice, move, result.targetIds())
                .map(event -> {
                    ArrayList<BattleEvent> events = new ArrayList<>(result.events());
                    events.add(event);
                    return new MultiTargetAppliedActionResult(events, result.targetIds());
                })
                .orElse(result);
    }
}
