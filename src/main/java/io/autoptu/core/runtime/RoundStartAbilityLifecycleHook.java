package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.LifecycleHook;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Executes the Python-parity round-start ability plan from the authoritative lifecycle.
 *
 * <p>The hook owns orchestration only. Family behavior remains in
 * {@link RoundStartAbilityEffectRegistry}; unsupported families stay explicit unhandled
 * invocations until their Python behavior is frozen and implemented.</p>
 */
public final class RoundStartAbilityLifecycleHook implements LifecycleHook {
    private final CombatantRuleContentRegistry ruleContent;

    public RoundStartAbilityLifecycleHook(CombatantRuleContentRegistry ruleContent) {
        this.ruleContent = Objects.requireNonNull(ruleContent, "ruleContent");
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        BattleRuntimeState state = context.state();
        List<RoundStartAbilityDispatchPlan.Combatant> combatants = state.combatantIds().stream()
                .map(combatantId -> new RoundStartAbilityDispatchPlan.Combatant(
                        combatantId,
                        state.isActive(combatantId),
                        state.requireCombatant(combatantId).hp() <= 0
                ))
                .toList();

        List<RoundStartAbilityDispatchPlan.Invocation> plan = RoundStartAbilityDispatchPlan.plan(
                state.environment().weather(),
                ActiveAbilityHolderResolver.resolve(state, "Air Lock"),
                combatants
        );

        RoundStartAbilityExecutionContext executionContext = RoundStartAbilityExecutionContext.of(state, ruleContent);
        RoundStartAbilityEffectRegistry registry = RoundStartAbilityEffectRegistry.pythonParityBuiltins(executionContext);
        List<RoundStartAbilityInvocationExecutor.InvocationResult> results =
                RoundStartAbilityInvocationExecutor.execute(plan, state, registry);

        ArrayList<BattleEvent> events = new ArrayList<>();
        for (RoundStartAbilityInvocationExecutor.InvocationResult result : results) {
            if (result.handled()) {
                events.addAll(result.events());
            }
        }
        return LifecycleHookResult.events(events);
    }
}
