package io.autoptu.core.runtime;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.PreResolutionTargetContext;
import io.autoptu.core.hook.PreResolutionTargetHook;
import io.autoptu.core.hook.PreResolutionTargetResult;

import java.util.Map;

/**
 * Core-only PRE-target bridge for interception.
 *
 * <p>The planner is intentionally package-private. External orchestration may identify the
 * Python-normalized Intercept kind and provide canonical combatant rule content, but it cannot
 * materialize eligible candidates or spatial attempts. Discovery, expiry cleanup, candidate
 * ordering, line geometry, Shift legality, RNG/resource use and displacement remain authoritative
 * runtime decisions.</p>
 */
final class RuntimeInterceptPreResolutionTargetHook implements PreResolutionTargetHook {
    @FunctionalInterface
    interface AttemptPlanner {
        Plan plan(PreResolutionTargetContext context, String currentTargetId);
    }

    record Plan(
            String interceptKind,
            Map<String, CombatantRuleContent> contentByCombatant
    ) {
        Plan {
            if (!"melee".equals(interceptKind) && !"ranged".equals(interceptKind)) {
                throw new IllegalArgumentException("interceptKind must be melee or ranged");
            }
            contentByCombatant = contentByCombatant == null ? Map.of() : Map.copyOf(contentByCombatant);
            if (contentByCombatant.entrySet().stream()
                    .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
                throw new IllegalArgumentException("contentByCombatant cannot contain null keys or values");
            }
        }

        boolean melee() {
            return "melee".equals(interceptKind);
        }
    }

    private final AttemptPlanner planner;

    RuntimeInterceptPreResolutionTargetHook(AttemptPlanner planner) {
        if (planner == null) throw new IllegalArgumentException("planner is required");
        this.planner = planner;
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

        RuntimeInterceptAttemptPlanner.Result attemptPlan = RuntimeInterceptAttemptPlanner.plan(
                context.state(),
                context.attackerId(),
                current.targetId(),
                plan.interceptKind(),
                plan.contentByCombatant()
        );
        if (attemptPlan.attempts().isEmpty()) return current;

        RuntimeInterceptSpatialSequenceApplication.Result resolution =
                RuntimeInterceptSpatialSequenceApplication.apply(
                        context.state(),
                        context.attackerId(),
                        current.targetId(),
                        plan.melee(),
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
