package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookRegistry;
import io.autoptu.core.hook.LifecycleHookResult;

import java.util.function.Consumer;

/**
 * Server-authoritative materializer for the frozen Python switch-entry transaction prefix.
 *
 * <p>The executor validates the complete switch plan and field-presence preconditions before the
 * first mutation. It then applies activity, presence and entry markers in the exact order frozen
 * by {@link CombatantSwitchExecutionPlan}, dispatches the generic
 * {@link LifecycleHookPoint#COMBATANT_ENTRY} seam, and finally executes the ordered post-entry
 * stage plan through {@link CombatantSwitchPostEntryDispatcher}. Minecraft/Cobblemon/Craftics
 * adapters may request or render a switch but never perform these rule-state mutations themselves.</p>
 *
 * <p>The convenience overloads use the current parity-safe production post-entry registry. The
 * explicit dispatcher overload remains available for differential probes and future composition.
 * Families whose Python contracts are not yet frozen remain pending rather than executing guessed
 * behavior.</p>
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
        return execute(
                state,
                fieldPresence,
                lifecycleHooks,
                CombatantSwitchPostEntryDispatchers.paritySafe(),
                event -> {},
                outgoingId,
                replacementId
        );
    }

    public static ExecutionResult execute(
            BattleRuntimeState state,
            CombatantFieldPresenceStore fieldPresence,
            LifecycleHookRegistry lifecycleHooks,
            Consumer<BattleEvent> eventSink,
            String outgoingId,
            String replacementId
    ) {
        return execute(
                state,
                fieldPresence,
                lifecycleHooks,
                CombatantSwitchPostEntryDispatchers.paritySafe(),
                eventSink,
                outgoingId,
                replacementId
        );
    }

    public static ExecutionResult execute(
            BattleRuntimeState state,
            CombatantFieldPresenceStore fieldPresence,
            LifecycleHookRegistry lifecycleHooks,
            CombatantSwitchPostEntryDispatcher postEntryDispatcher,
            Consumer<BattleEvent> eventSink,
            String outgoingId,
            String replacementId
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (fieldPresence == null) throw new IllegalArgumentException("field presence store is required");
        if (lifecycleHooks == null) throw new IllegalArgumentException("lifecycle hook registry is required");
        if (postEntryDispatcher == null) throw new IllegalArgumentException("post-entry dispatcher is required");
        if (eventSink == null) throw new IllegalArgumentException("event sink is required");

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
        for (BattleEvent event : entryResult.events()) {
            eventSink.accept(event);
        }

        CombatantSwitchPostEntryDispatcher.DispatchResult postEntryResult = postEntryDispatcher.dispatch(
                CombatantSwitchPostEntryPlan.pinnedContract(),
                new CombatantSwitchPostEntryDispatcher.DispatchContext(
                        state,
                        transition.replacementId(),
                        "start",
                        round
                ),
                eventSink
        );
        return new ExecutionResult(plan, entryResult, postEntryResult);
    }

    public record ExecutionResult(
            CombatantSwitchExecutionPlan plan,
            LifecycleHookResult entryHookResult,
            CombatantSwitchPostEntryDispatcher.DispatchResult postEntryDispatchResult
    ) {
        public ExecutionResult {
            if (plan == null) throw new IllegalArgumentException("switch execution plan is required");
            if (entryHookResult == null) throw new IllegalArgumentException("entry hook result is required");
            if (postEntryDispatchResult == null) {
                throw new IllegalArgumentException("post-entry dispatch result is required");
            }
        }
    }
}
