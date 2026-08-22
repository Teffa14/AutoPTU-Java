package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;

/**
 * Target-resolution request for a matured delayed hit.
 *
 * Python preserves the originally scheduled target id while resolving the target anchor
 * from the defender's live position when that defender still exists, otherwise from the
 * stored target position. The original move targeting model remains on {@link #move()} and
 * must not be rewritten to TILE merely because the defender disappeared.
 */
public record DelayedHitTargetRequest(
        DelayedHitEntry entry,
        MoveOption move,
        String targetId,
        GridCoord resolvedTargetPosition,
        boolean targetPresent
) {
    public DelayedHitTargetRequest {
        if (entry == null) throw new IllegalArgumentException("entry is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (!entry.moveId().equals(move.moveId())) {
            throw new IllegalArgumentException("move identity does not match delayed entry");
        }
        if (targetPresent && (targetId == null || targetId.isBlank())) {
            throw new IllegalArgumentException("present target requires target identity");
        }
        if (targetId == null && resolvedTargetPosition == null) {
            throw new IllegalArgumentException("delayed target requires identity or resolved position");
        }
    }

    public boolean missingStoredCombatant() {
        return targetId != null && !targetId.isBlank() && !targetPresent;
    }

    public boolean positionOnly() {
        return targetId == null || targetId.isBlank();
    }
}
