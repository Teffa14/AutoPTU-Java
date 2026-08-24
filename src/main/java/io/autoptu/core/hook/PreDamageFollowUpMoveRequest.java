package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;

/**
 * Server-owned request for a synchronous follow-up resolution of the original incoming move.
 *
 * Python PRE-damage reactions such as Sway and Magic Coat re-enter move target resolution with
 * the same MoveSpec while changing attacker/target identity. The runtime owns the actual move,
 * RNG, action-economy policy, and state mutation; hooks only describe the requested identities.
 */
public record PreDamageFollowUpMoveRequest(
        String attackerId,
        String targetId,
        GridCoord targetPosition
) {
    public PreDamageFollowUpMoveRequest {
        attackerId = require(attackerId, "attackerId");
        targetId = require(targetId, "targetId");
    }

    public static PreDamageFollowUpMoveRequest originalMove(
            String attackerId,
            String targetId,
            GridCoord targetPosition
    ) {
        return new PreDamageFollowUpMoveRequest(attackerId, targetId, targetPosition);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.strip();
    }
}
