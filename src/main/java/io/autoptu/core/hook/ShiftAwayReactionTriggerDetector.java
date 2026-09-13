package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.Targeting;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure spatial detector for the Attack of Opportunity Shift-away trigger.
 *
 * <p>The pinned Python oracle defines adjacency through footprint_distance(...). This detector
 * reuses the Java parity port of that geometry and only identifies the semantic transition. It
 * performs no action-window discovery, targeting, resource consumption, RNG, or state mutation.</p>
 */
public final class ShiftAwayReactionTriggerDetector {
    private ShiftAwayReactionTriggerDetector() {
    }

    public static Optional<ActionWindowTrigger> detect(
            GridCoord reactorAnchor,
            String reactorSize,
            GridCoord foeBeforeAnchor,
            GridCoord foeAfterAnchor,
            String foeSize
    ) {
        Objects.requireNonNull(reactorAnchor, "reactor anchor");
        Objects.requireNonNull(foeBeforeAnchor, "foe before anchor");
        Objects.requireNonNull(foeAfterAnchor, "foe after anchor");

        int beforeDistance = Targeting.footprintDistance(
                reactorAnchor,
                reactorSize,
                foeBeforeAnchor,
                foeSize
        );
        int afterDistance = Targeting.footprintDistance(
                reactorAnchor,
                reactorSize,
                foeAfterAnchor,
                foeSize
        );

        if (beforeDistance == 1 && afterDistance > 1) {
            return Optional.of(ActionWindowTrigger.ADJACENT_FOE_SHIFTS_AWAY);
        }
        return Optional.empty();
    }
}
