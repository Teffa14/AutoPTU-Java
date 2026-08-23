package io.autoptu.core.hook;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.CombatStageMutationOptions;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.Objects;

/** Server-owned context evaluated before a PTU combat-stage mutation is committed. */
public record CombatStagePreventionContext(
        BattleRuntimeState state,
        String attackerId,
        String targetId,
        String moveId,
        CombatStat stat,
        int requestedDelta,
        String effect,
        CombatStageMutationOptions options
) {
    public CombatStagePreventionContext {
        state = Objects.requireNonNull(state, "state");
        attackerId = required(attackerId, "attackerId");
        targetId = required(targetId, "targetId");
        moveId = moveId == null ? "" : moveId.strip();
        stat = Objects.requireNonNull(stat, "stat");
        effect = effect == null ? "" : effect.strip();
        options = options == null ? CombatStageMutationOptions.NONE : options;
    }

    public RuntimeCombatantState attacker() { return state.requireCombatant(attackerId); }
    public RuntimeCombatantState target() { return state.requireCombatant(targetId); }
    public boolean suppresses(String hookId) { return options.suppresses(hookId); }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }
}
