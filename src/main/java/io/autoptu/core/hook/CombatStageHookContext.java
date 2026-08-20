package io.autoptu.core.hook;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.Objects;

/**
 * Server-owned context passed to combat-stage reaction hooks after a stage change.
 *
 * Minecraft/Cobblemon may render the resulting events but cannot supply the current
 * stages, abilities, or applied delta used by this context.
 */
public record CombatStageHookContext(
        BattleRuntimeState state,
        String attackerId,
        String targetId,
        String moveId,
        CombatStat stat,
        int requestedDelta,
        int appliedDelta,
        String effect
) {
    public CombatStageHookContext {
        state = Objects.requireNonNull(state, "state");
        attackerId = required(attackerId, "attackerId");
        targetId = required(targetId, "targetId");
        moveId = moveId == null ? "" : moveId.strip();
        stat = Objects.requireNonNull(stat, "stat");
        effect = effect == null ? "" : effect.strip();
    }

    public RuntimeCombatantState attacker() {
        return state.requireCombatant(attackerId);
    }

    public RuntimeCombatantState target() {
        return state.requireCombatant(targetId);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
