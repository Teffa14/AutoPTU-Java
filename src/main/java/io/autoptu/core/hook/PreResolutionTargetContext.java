package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.runtime.BattleRuntimeState;

/** Server-owned context for target replacement that must happen before accuracy and damage. */
public record PreResolutionTargetContext(
        BattleRuntimeState state,
        String attackerId,
        String moveId,
        MoveOption move,
        String originalTargetId,
        GridCoord originalTargetAnchor
) {
    public PreResolutionTargetContext {
        if (state == null) throw new IllegalArgumentException("state is required");
        attackerId = require(attackerId, "attackerId");
        moveId = require(moveId, "moveId");
        if (move != null && !move.moveId().equals(moveId)) {
            throw new IllegalArgumentException("move metadata must match moveId");
        }
        originalTargetId = require(originalTargetId, "originalTargetId");
        if (originalTargetAnchor == null) throw new IllegalArgumentException("originalTargetAnchor is required");
    }

    /** Backwards-compatible context for hooks that do not require canonical move metadata. */
    public PreResolutionTargetContext(
            BattleRuntimeState state,
            String attackerId,
            String moveId,
            String originalTargetId,
            GridCoord originalTargetAnchor
    ) {
        this(state, attackerId, moveId, null, originalTargetId, originalTargetAnchor);
    }

    public MoveOption requireMove() {
        if (move == null) throw new IllegalStateException("authoritative move metadata is required");
        return move;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.strip();
    }
}
