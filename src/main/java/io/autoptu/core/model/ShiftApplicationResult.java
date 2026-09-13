package io.autoptu.core.model;

import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.rules.ActionSpendResult;

import java.util.Optional;

/** Pure transition output used by headless simulation and Minecraft playback. */
public record ShiftApplicationResult(
        GridCoord position,
        ShiftResolvedEvent event,
        ActionSpendResult actionSpend
) {
    public ShiftApplicationResult {
        if (position == null) throw new IllegalArgumentException("position is required");
        if (event == null) throw new IllegalArgumentException("event is required");
        if (!position.equals(event.destination())) {
            throw new IllegalArgumentException("position must equal event destination");
        }
    }

    /** Existing callers remain source-compatible while spend provenance is adopted. */
    public ShiftApplicationResult(GridCoord position, ShiftResolvedEvent event) {
        this(position, event, null);
    }

    /**
     * Optional authoritative action-economy provenance kept outside the semantic event.
     * Adapters can replay the same ShiftResolvedEvent without becoming rules engines.
     */
    public Optional<ActionSpendResult> actionSpendResult() {
        return Optional.ofNullable(actionSpend);
    }
}
