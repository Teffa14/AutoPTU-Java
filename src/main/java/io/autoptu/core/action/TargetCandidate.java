package io.autoptu.core.action;

import io.autoptu.core.model.GridCoord;

/**
 * Combatant already permitted by semantic targeting rules (ally/enemy/etc.).
 * This slice applies only geometry, footprint, LoS, and action-budget legality.
 */
public record TargetCandidate(
        String combatantId,
        GridCoord anchor,
        String sizeLabel
) {
    public TargetCandidate {
        if (combatantId == null || combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId is required");
        }
        if (anchor == null) {
            throw new IllegalArgumentException("anchor is required");
        }
        sizeLabel = sizeLabel == null || sizeLabel.isBlank() ? "Medium" : sizeLabel;
    }
}
