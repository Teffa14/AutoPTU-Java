package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.rules.MoveCannotMissResolution;
import io.autoptu.core.rules.Targeting;

/**
 * Materializes InterceptAttemptPolicy inputs exclusively from canonical move and battle state.
 * Minecraft/Cobblemon may select a move id or render the result, but cannot supply attempt gates.
 */
public final class RuntimeInterceptAttemptInputFactory {
    private RuntimeInterceptAttemptInputFactory() {}

    public static InterceptAttemptPolicy.Input fromState(
            BattleRuntimeState state,
            String attackerId,
            String interceptorId,
            MoveOption move
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (move == null) throw new IllegalArgumentException("move is required");

        RuntimeCombatantState attacker = state.requireCombatant(attackerId);
        RuntimeCombatantState interceptor = state.requireCombatant(interceptorId);
        MoveSpec spec = move.spec();

        boolean cannotMiss = MoveCannotMissResolution.resolve(move.moveId(), spec);
        boolean areaAttack = !Targeting.normalizedAreaKind(spec).isBlank();
        String targetKind = Targeting.normalizedTargetKind(spec);
        boolean priorityOrInterrupt = spec.priority() > 0 || spec.hasRangeKeyword("interrupt");

        // Python _attempt_intercept compares raw PokemonSpec.spd values here. It does not
        // apply Combat Stages, statuses, weather, abilities, initiative bonuses, or riders.
        int attackerSpeed = attacker.requireStatProfile().base(CombatStat.SPD);
        int interceptorSpeed = interceptor.requireStatProfile().base(CombatStat.SPD);

        return new InterceptAttemptPolicy.Input(
                cannotMiss,
                areaAttack,
                targetKind,
                priorityOrInterrupt,
                interceptorSpeed,
                attackerSpeed
        );
    }
}
