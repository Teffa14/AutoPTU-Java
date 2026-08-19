package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

/**
 * One server-owned delayed move resolution scheduled by PTU rules.
 *
 * Python stores the original MoveSpec object. Java stores the stable move identity and
 * resolves that identity against the attacker's canonical moveset when execution is
 * implemented. Minecraft/Cobblemon never supplies the trigger decision or result.
 */
public record DelayedHitEntry(
        String attackerId,
        String moveId,
        String targetId,
        GridCoord targetPosition,
        int triggerRound,
        String effect
) {
    public DelayedHitEntry {
        attackerId = requireText(attackerId, "attackerId");
        moveId = requireText(moveId, "moveId");
        targetId = normalizeOptional(targetId);
        if (triggerRound < 0) {
            throw new IllegalArgumentException("triggerRound cannot be negative");
        }
        effect = effect == null ? "" : effect.strip();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
