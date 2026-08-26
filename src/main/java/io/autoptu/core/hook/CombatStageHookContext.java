package io.autoptu.core.hook;

import io.autoptu.core.model.CombatStageStat;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.CombatStageMutationOptions;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.Objects;

/**
 * Server-owned context passed to combat-stage reaction hooks after a stage change.
 *
 * Minecraft/Cobblemon may render the resulting events but cannot supply the current
 * stages, abilities, applied delta, or recursive hook-suppression state used here.
 */
public record CombatStageHookContext(
        BattleRuntimeState state,
        String attackerId,
        String targetId,
        String moveId,
        CombatStageStat stat,
        int requestedDelta,
        int appliedDelta,
        String effect,
        CombatStageMutationOptions options
) {
    public CombatStageHookContext {
        state = Objects.requireNonNull(state, "state");
        attackerId = required(attackerId, "attackerId");
        targetId = required(targetId, "targetId");
        moveId = moveId == null ? "" : moveId.strip();
        stat = Objects.requireNonNull(stat, "stat");
        effect = effect == null ? "" : effect.strip();
        options = options == null ? CombatStageMutationOptions.NONE : options;
    }

    public CombatStageHookContext(
            BattleRuntimeState state,
            String attackerId,
            String targetId,
            String moveId,
            CombatStageStat stat,
            int requestedDelta,
            int appliedDelta,
            String effect
    ) {
        this(state, attackerId, targetId, moveId, stat, requestedDelta, appliedDelta, effect,
                CombatStageMutationOptions.NONE);
    }

    /** Source-compatible five-stat constructor for existing callers during migration. */
    public CombatStageHookContext(
            BattleRuntimeState state,
            String attackerId,
            String targetId,
            String moveId,
            CombatStat stat,
            int requestedDelta,
            int appliedDelta,
            String effect,
            CombatStageMutationOptions options
    ) {
        this(state, attackerId, targetId, moveId, CombatStageStat.fromCombatStat(stat),
                requestedDelta, appliedDelta, effect, options);
    }

    /** Source-compatible five-stat constructor for existing callers during migration. */
    public CombatStageHookContext(
            BattleRuntimeState state,
            String attackerId,
            String targetId,
            String moveId,
            CombatStat stat,
            int requestedDelta,
            int appliedDelta,
            String effect
    ) {
        this(state, attackerId, targetId, moveId, CombatStageStat.fromCombatStat(stat),
                requestedDelta, appliedDelta, effect, CombatStageMutationOptions.NONE);
    }

    public RuntimeCombatantState attacker() {
        return state.requireCombatant(attackerId);
    }

    public RuntimeCombatantState target() {
        return state.requireCombatant(targetId);
    }

    public boolean suppresses(String hookId) {
        return options.suppresses(hookId);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
