package io.autoptu.core.runtime;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.PreResolutionTargetContext;
import io.autoptu.core.hook.PreResolutionTargetHook;
import io.autoptu.core.hook.PreResolutionTargetResult;
import io.autoptu.core.rules.Targeting;

/**
 * Core-only PRE-target bridge for interception.
 *
 * <p>The planner is intentionally package-private and can no longer supply candidate content.
 * Intercept resolves canonical combatant rule content from the registry bound to this core hook.
 * Discovery, expiry cleanup, candidate ordering, line geometry, Shift legality, RNG/resource use
 * and displacement remain authoritative runtime decisions.</p>
 */
final class RuntimeInterceptPreResolutionTargetHook implements PreResolutionTargetHook {
    @FunctionalInterface
    interface AttemptPlanner {
        Plan plan(PreResolutionTargetContext context, String currentTargetId);
    }

    record Plan() {}

    private final AttemptPlanner planner;
    private final CombatantRuleContentRegistry ruleContent;

    RuntimeInterceptPreResolutionTargetHook(
            AttemptPlanner planner,
            CombatantRuleContentRegistry ruleContent
    ) {
        if (planner == null) throw new IllegalArgumentException("planner is required");
        if (ruleContent == null) throw new IllegalArgumentException("ruleContent is required");
        this.planner = planner;
        this.ruleContent = ruleContent;
    }

    @Override
    public PreResolutionTargetResult resolve(
            PreResolutionTargetContext context,
            PreResolutionTargetResult current
    ) {
        if (context == null) throw new IllegalArgumentException("context is required");
        if (current == null) throw new IllegalArgumentException("current result is required");

        Plan plan = planner.plan(context, current.targetId());
        if (plan == null) return current;

        String normalizedTargetKind = Targeting.normalizedTargetKind(context.requireMove().spec());
        String interceptKind = InterceptKindResolution.fromNormalizedTargetKind(normalizedTargetKind);
        boolean melee = InterceptKindResolution.isMelee(normalizedTargetKind);

        RuntimeInterceptAttemptPlanner.Result attemptPlan = RuntimeInterceptAttemptPlanner.plan(
                context.state(),
                context.attackerId(),
                current.targetId(),
                interceptKind,
                ruleContent
        );
        if (attemptPlan.attempts().isEmpty()) return current;

        RuntimeInterceptSpatialSequenceApplication.Result resolution =
                RuntimeInterceptSpatialSequenceApplication.apply(
                        context.state(),
                        context.attackerId(),
                        current.targetId(),
                        melee,
                        attemptPlan.attempts()
                );
        if (!resolution.intercepted()) return current;

        String replacement = resolution.replacementTargetId();
        RuntimeCombatantState interceptor = context.state().requireCombatant(replacement);
        RuleEffectEvent event = new RuleEffectEvent(
                "reaction",
                "Intercept",
                replacement,
                current.targetId(),
                context.moveId(),
                "target_replaced",
                0.0,
                interceptor.hp()
        );
        return current.replaceTarget(replacement, java.util.List.of(event));
    }
}
