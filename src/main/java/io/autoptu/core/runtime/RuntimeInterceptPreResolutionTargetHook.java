package io.autoptu.core.runtime;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.PreResolutionTargetContext;
import io.autoptu.core.hook.PreResolutionTargetHook;
import io.autoptu.core.hook.PreResolutionTargetResult;

import java.util.List;

/**
 * Core-only PRE-target bridge for interception.
 *
 * <p>The planner is intentionally package-private: only authoritative runtime orchestration may
 * materialize interception attempts. The hook executes the existing RNG/resource/spatial sequence
 * and exposes only the resulting replacement target plus semantic playback event to the generic
 * PRE-target registry. Attack-line geometry is derived inside the runtime from authoritative
 * attacker and target positions.</p>
 */
final class RuntimeInterceptPreResolutionTargetHook implements PreResolutionTargetHook {
    @FunctionalInterface
    interface AttemptPlanner {
        Plan plan(PreResolutionTargetContext context, String currentTargetId);
    }

    record Plan(
            boolean melee,
            List<RuntimeInterceptSpatialSequenceApplication.Attempt> attempts
    ) {
        Plan {
            attempts = attempts == null ? List.of() : List.copyOf(attempts);
            if (attempts.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("attempts cannot contain null");
            }
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
        if (plan == null || plan.attempts().isEmpty()) return current;

        RuntimeInterceptSpatialSequenceApplication.Result resolution =
                RuntimeInterceptSpatialSequenceApplication.apply(
                        context.state(),
                        context.attackerId(),
                        current.targetId(),
                        plan.melee(),
                        plan.attempts()
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
        return current.replaceTarget(replacement, List.of(event));
    }
}
