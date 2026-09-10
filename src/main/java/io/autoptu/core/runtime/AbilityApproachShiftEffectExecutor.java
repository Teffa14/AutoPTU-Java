package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.ArrayList;
import java.util.List;

/**
 * Materializes stateful side effects shared by abilities that force their holders to make a legal
 * approach Shift. Movement stays delegated to {@link AbilityApproachShiftExecutor}; this layer adds
 * the temporary marker and language-neutral semantic event descriptor required by the ability hook.
 */
public final class AbilityApproachShiftEffectExecutor {
    private AbilityApproachShiftEffectExecutor() {
    }

    public static List<EffectResult> execute(
            BattleRuntimeState state,
            String abilityName,
            String targetId,
            String temporaryEffectName
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (abilityName == null || abilityName.isBlank()) throw new IllegalArgumentException("abilityName is required");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");
        if (temporaryEffectName == null || temporaryEffectName.isBlank()) {
            throw new IllegalArgumentException("temporaryEffectName is required");
        }
        state.requireCombatant(targetId);

        ArrayList<EffectResult> results = new ArrayList<>();
        for (AbilityApproachShiftExecutor.ShiftResult shift : AbilityApproachShiftExecutor.execute(state, abilityName, targetId)) {
            RuntimeCombatantState actor = state.requireCombatant(shift.actorId());
            actor.temporaryEffects().add(temporaryEffectName);
            results.add(new EffectResult(
                    shift.actorId(),
                    shift.ability(),
                    targetId,
                    shift.from(),
                    shift.to(),
                    temporaryEffectName.strip()
            ));
        }
        return List.copyOf(results);
    }

    /** Ordered semantic descriptor; an outer lifecycle/event sink may publish it without owning rules. */
    public record EffectResult(
            String actorId,
            String ability,
            String targetId,
            GridCoord from,
            GridCoord to,
            String temporaryEffect
    ) {
        public EffectResult {
            if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
            if (ability == null || ability.isBlank()) throw new IllegalArgumentException("ability is required");
            if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");
            if (from == null || to == null) throw new IllegalArgumentException("shift coordinates are required");
            if (temporaryEffect == null || temporaryEffect.isBlank()) {
                throw new IllegalArgumentException("temporaryEffect is required");
            }
        }
    }
}
