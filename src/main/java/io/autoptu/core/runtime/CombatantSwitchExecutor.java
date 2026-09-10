package io.autoptu.core.runtime;

import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookRegistry;
import io.autoptu.core.hook.LifecycleHookResult;

/**
 * Server-authoritative materializer for the frozen Python switch-entry transaction prefix.
 *
 * <p>The executor validates the complete switch plan and field-presence preconditions before the
 * first mutation. It then applies activity, presence and entry markers in the exact order frozen
 * by {@link CombatantSwitchExecutionPlan}, and only afterwards dispatches the generic
 * {@link LifecycleHookPoint#COMBATANT_ENTRY} seam. Minecraft/Cobblemon/Craftics adapters may request
 * or render a switch but never perform these rule-state mutations themselves.</p>
 *
 * <p>Mount/rider synchronization, send-out Trainer Features beyond registered entry hooks,
 * hazards/zones, initiative replacement semantics and action-economy consumption remain outside
 * this bounded prefix until their Python contracts are frozen separately.</p>
 */
public final class CombatantSwitchExecutor {
    private CombatantSwitchExecutor() {}

    public static ExecutionResult execute(
            BattleRuntimeState state,
            CombatantFieldPresenceStore fieldPresence,
            LifecycleHookRegistry lifecycleHooks,
            String outgoingId,
            String replacementId
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (fieldPresence == null) throw new IllegalArgumentException("field presence store is required");
        if (lifecycleHooks == null) throw new IllegalArgumentException("lifecycle hook registry is required");

        CombatantSwitchExecutionPlan plan = CombatantSwitchExecutionPlan.resolve(state, outgoingId, replacementId);
        CombatantSwitchTransitionPlan transition = plan.transition();

        if (!fieldPresence.isOnField(transition.outgoingId())) {
            throw new IllegalArgumentException("outgoing combatant must be on-field");
        }
        if (fieldPresence.isOnField(transition.replacementId())) {
            throw new IllegalArgumentException("replacement combatant must be off-field");
        }
        if (!fieldPresence.position(transition.outgoingId())
                .orElseThrow(() -> new IllegalArgumentException("outgoing field position is required"))
                .equals(transition.replacementDestination())) {
            throw new IllegalArgumentException("field presence disagrees with outgoing switch destination");
        }

        CombatantAffiliationState outgoingAffiliation = state.affiliation(transition.outgoingId());
        CombatantAffiliationState replacementAffiliation = state.affiliation(transition.replacementId());
        RuntimeCombatantState replacement = state.requireCombatant(transition.replacementId());

        outgoingAffiliation.setActiveFromRuntime(transition.outgoingActiveAfter());
        fieldPresence.removeFromRuntime(transition.outgoingId());
        replacementAffiliation.setActiveFromRuntime(transition.replacementActiveAfter());
        fieldPresence.placeFromRuntime(transition.replacementId(), transition.replacementDestination());
        replacement.moveTo(transition.replacementDestination());
        plan.entryState().applyTemporaryEffects(state);

        int round = state.currentRound();
        LifecycleHookResult entryResult = lifecycleHooks.resolve(
                LifecycleHookPoint.COMBATANT_ENTRY,
                new LifecycleHookContext(
                        state,
                        state.damageHistory(),
                        state.injuryHistory(),
                        LifecycleHookPoint.COMBATANT_ENTRY,
                        round,
                        round,
                        transition.replacementId()
                )
        );
        return new ExecutionResult(plan, entryResult);
    }

    public record ExecutionResult(
            CombatantSwitchExecutionPlan plan,
            LifecycleHookResult entryHookResult
    ) {
        public ExecutionResult {
            if (plan == null) throw new IllegalArgumentException("switch execution plan is required");
            if (entryHookResult == null) throw new IllegalArgumentException("entry hook result is required");
        }
    }
}
