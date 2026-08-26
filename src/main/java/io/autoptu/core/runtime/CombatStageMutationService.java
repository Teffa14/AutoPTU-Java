package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.BuiltinCombatStageHooks;
import io.autoptu.core.hook.BuiltinCombatStagePreventionHooks;
import io.autoptu.core.hook.CombatStageHookContext;
import io.autoptu.core.hook.CombatStageHookPhase;
import io.autoptu.core.hook.CombatStageHookRegistry;
import io.autoptu.core.hook.CombatStageHookResult;
import io.autoptu.core.hook.CombatStagePreventionContext;
import io.autoptu.core.hook.CombatStagePreventionHookRegistry;
import io.autoptu.core.hook.CombatStagePreventionResult;
import io.autoptu.core.model.CombatStageStat;
import io.autoptu.core.model.CombatStat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Single server-authoritative mutation boundary for PTU combat stages that use the
 * Python BattleState-style combat-stage hook pipeline.
 *
 * Minecraft/Cobblemon may request an action that eventually causes a stage change,
 * but cannot supply the current stage, prevention result, or applied delta. The core
 * evaluates PRE_APPLY blockers first, commits the clamped stage change only if it is
 * allowed, then runs POST_APPLY reactions.
 *
 * Some pinned Python mechanics intentionally mutate combat_stages directly (for
 * example the Link Trainer Features). Those callers should remain direct until the
 * Python oracle says they participate in combat-stage reactions.
 */
public final class CombatStageMutationService {
    private final BattleRuntimeState state;
    private final CombatStageHookRegistry hooks;
    private final CombatStagePreventionHookRegistry preventions;

    /** Compatibility constructor for tests/custom callers that only provide post-apply reactions. */
    public CombatStageMutationService(BattleRuntimeState state, CombatStageHookRegistry hooks) {
        this(state, hooks, CombatStagePreventionHookRegistry.empty());
    }

    public CombatStageMutationService(
            BattleRuntimeState state,
            CombatStageHookRegistry hooks,
            CombatStagePreventionHookRegistry preventions
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.preventions = Objects.requireNonNull(preventions, "preventions");
    }

    public static CombatStageMutationService authoritative(BattleRuntimeState state) {
        return new CombatStageMutationService(
                state,
                BuiltinCombatStageHooks.registry(),
                BuiltinCombatStagePreventionHooks.registry()
        );
    }

    /** Compatibility boundary for existing five-stat callers. */
    public CombatStageMutationResult apply(
            String attackerId,
            String targetId,
            String moveId,
            CombatStat stat,
            int requestedDelta,
            String effect
    ) {
        return apply(
                attackerId,
                targetId,
                moveId,
                CombatStageStat.fromCombatStat(stat),
                requestedDelta,
                effect,
                CombatStageMutationOptions.NONE
        );
    }

    /** Canonical seven-Combat-Stage mutation boundary. */
    public CombatStageMutationResult apply(
            String attackerId,
            String targetId,
            String moveId,
            CombatStageStat stat,
            int requestedDelta,
            String effect
    ) {
        return apply(attackerId, targetId, moveId, stat, requestedDelta, effect, CombatStageMutationOptions.NONE);
    }

    /** Compatibility recursive boundary for existing five-stat callers. */
    public CombatStageMutationResult apply(
            String attackerId,
            String targetId,
            String moveId,
            CombatStat stat,
            int requestedDelta,
            String effect,
            CombatStageMutationOptions options
    ) {
        return apply(
                attackerId,
                targetId,
                moveId,
                CombatStageStat.fromCombatStat(stat),
                requestedDelta,
                effect,
                options
        );
    }

    /**
     * Internal recursive boundary used when Python suppresses a specific reaction
     * while re-entering the same combat-stage pipeline.
     */
    public CombatStageMutationResult apply(
            String attackerId,
            String targetId,
            String moveId,
            CombatStageStat stat,
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
        CombatStagePreventionResult prevention = preventions.apply(new CombatStagePreventionContext(
                state,
                canonicalAttackerId,
                targetId,
                moveId,
                stat,
                requestedDelta,
                effect,
                canonicalOptions
        ));
        if (prevention.blocked()) {
            return new CombatStageMutationResult(
                    startingStage,
                    requestedDelta,
                    0,
                    startingStage,
                    startingStage,
                    prevention.events()
            );
        }

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
        List<BattleEvent> events;
        if (prevention.events().isEmpty()) {
            events = hookResult.events();
        } else {
            ArrayList<BattleEvent> combined = new ArrayList<>(prevention.events());
            combined.addAll(hookResult.events());
            events = List.copyOf(combined);
        }

        return new CombatStageMutationResult(
                startingStage,
                requestedDelta,
                baseAppliedDelta,
                baseStage,
                finalStage,
                events
        );
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
