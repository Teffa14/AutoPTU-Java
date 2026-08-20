package io.autoptu.core.runtime;

import io.autoptu.core.hook.BuiltinCombatStageHooks;
import io.autoptu.core.hook.CombatStageHookContext;
import io.autoptu.core.hook.CombatStageHookPhase;
import io.autoptu.core.hook.CombatStageHookRegistry;
import io.autoptu.core.hook.CombatStageHookResult;
import io.autoptu.core.model.CombatStat;

import java.util.Objects;

/**
 * Single server-authoritative mutation boundary for PTU combat stages that use the
 * Python BattleState-style combat-stage hook pipeline.
 *
 * Minecraft/Cobblemon may request an action that eventually causes a stage change,
 * but cannot supply the current stage or the applied delta. Both are derived here
 * from the canonical BattleRuntimeState before POST_APPLY reactions execute.
 *
 * Some pinned Python mechanics intentionally mutate combat_stages directly (for
 * example the Link Trainer Features). Those callers should remain direct until the
 * Python oracle says they participate in combat-stage reactions.
 */
public final class CombatStageMutationService {
    private final BattleRuntimeState state;
    private final CombatStageHookRegistry hooks;

    public CombatStageMutationService(BattleRuntimeState state, CombatStageHookRegistry hooks) {
        this.state = Objects.requireNonNull(state, "state");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
    }

    public static CombatStageMutationService authoritative(BattleRuntimeState state) {
        return new CombatStageMutationService(state, BuiltinCombatStageHooks.registry());
    }

    public CombatStageMutationResult apply(
            String attackerId,
            String targetId,
            String moveId,
            CombatStat stat,
            int requestedDelta,
            String effect
    ) {
        return apply(attackerId, targetId, moveId, stat, requestedDelta, effect, CombatStageMutationOptions.NONE);
    }

    /**
     * Internal recursive boundary used when Python suppresses a specific reaction
     * while re-entering the same combat-stage pipeline.
     */
    public CombatStageMutationResult apply(
            String attackerId,
            String targetId,
            String moveId,
            CombatStat stat,
            int requestedDelta,
            String effect,
            CombatStageMutationOptions options
    ) {
        RuntimeCombatantState target = state.requireCombatant(required(targetId, "targetId"));
        String canonicalAttackerId = required(attackerId, "attackerId");
        state.requireCombatant(canonicalAttackerId);
        Objects.requireNonNull(stat, "stat");
        CombatStageMutationOptions canonicalOptions = options == null ? CombatStageMutationOptions.NONE : options;

        int startingStage = target.combatStages().get(stat);
        int baseStage = target.combatStages().adjust(stat, requestedDelta);
        int baseAppliedDelta = baseStage - startingStage;

        CombatStageHookContext context = new CombatStageHookContext(
                state,
                canonicalAttackerId,
                targetId,
                moveId,
                stat,
                requestedDelta,
                baseAppliedDelta,
                effect,
                canonicalOptions
        );
        CombatStageHookResult hookResult = hooks.apply(CombatStageHookPhase.POST_APPLY, context);
        int finalStage = target.combatStages().get(stat);

        return new CombatStageMutationResult(
                startingStage,
                requestedDelta,
                baseAppliedDelta,
                baseStage,
                finalStage,
                hookResult.events()
        );
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
