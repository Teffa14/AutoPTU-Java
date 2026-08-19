package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;

/**
 * Canonical delayed-hit execution inputs resolved from server-owned battle state.
 *
 * The delayed entry preserves the scheduling contract, while move and choice are
 * rebound at execution time so Minecraft/Cobblemon cannot inject a different move,
 * target identity, or target anchor.
 */
public record DelayedHitBinding(
        DelayedHitEntry entry,
        MoveOption move,
        MoveChoice choice
) {
    public DelayedHitBinding {
        if (entry == null) throw new IllegalArgumentException("entry is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (!entry.attackerId().equals(choice.actorId())) {
            throw new IllegalArgumentException("choice actor does not match delayed attacker");
        }
        if (!entry.moveId().equals(move.moveId()) || !entry.moveId().equals(choice.moveId())) {
            throw new IllegalArgumentException("move identity does not match delayed entry");
        }
    }
}
