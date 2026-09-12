package io.autoptu.core.runtime;

import io.autoptu.core.hook.BuiltinSwitchTriggerPlanners;
import io.autoptu.core.hook.SwitchTriggerDecisionPlan;
import io.autoptu.core.hook.SwitchTriggerPlannerRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server-authoritative dispatcher for reactive switch-trigger planning.
 *
 * <p>Feature-specific pre-dispatch guards are read and, when required, armed before the Feature
 * planner runs. The planner receives the immutable pre-arm observation so a newly armed guard does
 * not suppress the first valid trigger. This matches Python's ally-faint Quick Switch sequence:
 * check guard, arm guard, then dispatch Quick Switch.</p>
 */
public final class RuntimeSwitchTriggerDispatcher {
    private final SwitchTriggerPlannerRegistry planners;
    private final Map<GuardKey, GuardRule> guardRules;

    public RuntimeSwitchTriggerDispatcher(
            SwitchTriggerPlannerRegistry planners,
            List<GuardRule> guardRules
    ) {
        if (planners == null) throw new IllegalArgumentException("planner registry is required");
        this.planners = planners;
        LinkedHashMap<GuardKey, GuardRule> copy = new LinkedHashMap<>();
        if (guardRules != null) {
            for (GuardRule rule : guardRules) {
                if (rule == null) throw new IllegalArgumentException("guard rule is required");
                GuardKey key = new GuardKey(normalizeFeature(rule.featureName()), rule.trigger());
                if (copy.put(key, rule) != null) {
                    throw new IllegalArgumentException("duplicate switch trigger guard rule: " + key);
                }
            }
        }
        this.guardRules = Map.copyOf(copy);
    }

    public static RuntimeSwitchTriggerDispatcher paritySafe() {
        return new RuntimeSwitchTriggerDispatcher(
                BuiltinSwitchTriggerPlanners.paritySafe(),
                List.of(new GuardRule(
                        "Quick Switch",
                        SwitchTriggerDecisionPlan.Trigger.ALLY_FAINT,
                        "quick_switch_faint_handled",
                        true
                ))
        );
    }

    /**
     * Dispatch one runtime trigger into zero or more optional switch decisions.
     *
     * @param triggerTargetId combatant that owns trigger-scoped guard state, when the matching
     *                        Feature rule requires one
     */
    public DispatchResult dispatch(
            BattleRuntimeState state,
            SwitchTriggerDecisionPlan.Trigger trigger,
            String actorId,
            String triggerTargetId
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (trigger == null) throw new IllegalArgumentException("trigger is required");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");

        ArrayList<SwitchTriggerDecisionPlan> decisions = new ArrayList<>();
        ArrayList<GuardObservation> guards = new ArrayList<>();

        for (String featureName : planners.featureNames()) {
            GuardRule rule = guardRules.get(new GuardKey(featureName, trigger));
            SwitchTriggerPlannerRegistry.PlanningContext context;
            if (rule == null) {
                context = RuntimeSwitchTriggerPlanningContextFactory.fromState(state, trigger, actorId);
            } else {
                RuntimeCombatantState target = requireGuardTarget(state, triggerTargetId);
                boolean alreadyHandled = target.temporaryEffects().has(rule.effectKey());

                // Freeze the pre-arm observation into the planner context before mutating the
                // canonical store. Re-reading after arm would incorrectly suppress this first hit.
                context = RuntimeSwitchTriggerPlanningContextFactory.fromState(
                        state,
                        trigger,
                        actorId,
                        triggerTargetId,
                        rule.effectKey()
                );

                boolean armed = false;
                if (!alreadyHandled) {
                    if (rule.roundScoped()) {
                        int round = state.currentRound();
                        target.temporaryEffects().add(
                                rule.effectKey(),
                                Map.of("round", round, "expires_round", round)
                        );
                    } else {
                        target.temporaryEffects().add(rule.effectKey());
                    }
                    armed = true;
                }
                guards.add(new GuardObservation(
                        featureName,
                        trigger,
                        target.combatantId(),
                        rule.effectKey(),
                        alreadyHandled,
                        armed
                ));
            }

            planners.plansForFeature(featureName, context).ifPresent(decisions::add);
        }

        return new DispatchResult(List.copyOf(decisions), List.copyOf(guards));
    }

    private static RuntimeCombatantState requireGuardTarget(BattleRuntimeState state, String triggerTargetId) {
        if (triggerTargetId == null || triggerTargetId.isBlank()) {
            throw new IllegalArgumentException("triggerTargetId is required for guarded switch trigger");
        }
        return state.requireCombatant(triggerTargetId.strip());
    }

    private static String normalizeFeature(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("featureName is required");
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private record GuardKey(String featureName, SwitchTriggerDecisionPlan.Trigger trigger) {
        private GuardKey {
            if (featureName == null || featureName.isBlank()) {
                throw new IllegalArgumentException("featureName is required");
            }
            if (trigger == null) throw new IllegalArgumentException("trigger is required");
        }
    }

    public record GuardRule(
            String featureName,
            SwitchTriggerDecisionPlan.Trigger trigger,
            String effectKey,
            boolean roundScoped
    ) {
        public GuardRule {
            if (featureName == null || featureName.isBlank()) {
                throw new IllegalArgumentException("featureName is required");
            }
            featureName = featureName.strip();
            if (trigger == null) throw new IllegalArgumentException("trigger is required");
            if (effectKey == null || effectKey.isBlank()) {
                throw new IllegalArgumentException("effectKey is required");
            }
            effectKey = effectKey.strip().toLowerCase(Locale.ROOT);
        }
    }

    public record GuardObservation(
            String featureName,
            SwitchTriggerDecisionPlan.Trigger trigger,
            String triggerTargetId,
            String effectKey,
            boolean alreadyHandled,
            boolean armed
    ) {
    }

    public record DispatchResult(
            List<SwitchTriggerDecisionPlan> decisions,
            List<GuardObservation> guards
    ) {
        public DispatchResult {
            decisions = decisions == null ? List.of() : List.copyOf(decisions);
            guards = guards == null ? List.of() : List.copyOf(guards);
        }
    }
}
