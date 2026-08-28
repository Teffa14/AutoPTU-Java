package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.runtime.BattleRuntimeState;

/** Server-owned context for target replacement that must happen before accuracy and damage. */
public record PreResolutionTargetContext(
        BattleRuntimeState state,
        String attackerId,
        String moveId,
        String originalTargetId,
        GridCoord originalTargetAnchor
) {
    public PreResolutionTargetContext {
        if (state == null) throw new IllegalArgumentException("state is required");
        attackerId = require(attackerId, "attackerId");
        moveId = require(moveId, "moveId");
        originalTargetId = require(originalTargetId, "originalTargetId");
        if (originalTargetAnchor == null) throw new IllegalArgumentException("originalTargetAnchor is required");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.strip();
    }
}
