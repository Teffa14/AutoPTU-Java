package io.autoptu.core.hook;

import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.StatusEntry;

import java.util.Objects;

/** Authoritative context for attempting to apply one status to a combatant. */
public record StatusApplicationContext(
        BattleRuntimeState state,
        String sourceActorId,
        String targetId,
        StatusEntry status,
        String sourceKind,
        String sourceName,
        String moveId
) {
    public StatusApplicationContext {
        state = Objects.requireNonNull(state, "state");
        sourceActorId = safe(sourceActorId);
        targetId = require(targetId, "targetId");
        status = Objects.requireNonNull(status, "status");
        sourceKind = safe(sourceKind);
        sourceName = safe(sourceName);
        moveId = safe(moveId);
        state.requireCombatant(targetId);
        if (!sourceActorId.isBlank()) state.requireCombatant(sourceActorId);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static String require(String value, String name) {
        String normalized = safe(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " is required");
        return normalized;
    }
}
