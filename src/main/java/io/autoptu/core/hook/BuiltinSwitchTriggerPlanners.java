package io.autoptu.core.hook;

import java.util.Optional;

/** Pinned Python-backed switch-trigger planner registrations. */
public final class BuiltinSwitchTriggerPlanners {
    private static final int QUICK_SWITCH_AP = 2;
    private static final String QUICK_SWITCH = "Quick Switch";
    private static final String QUICK_SWITCH_SENT_OUT = "quick_switch_sent_out";
    private static final String QUICK_SWITCH_FAINT_HANDLED = "quick_switch_faint_handled";

    private BuiltinSwitchTriggerPlanners() {
    }

    public static SwitchTriggerPlannerRegistry paritySafe() {
        return SwitchTriggerPlannerRegistry.empty()
                .withPlanner(QUICK_SWITCH, BuiltinSwitchTriggerPlanners::planQuickSwitch);
    }

    private static Optional<SwitchTriggerDecisionPlan> planQuickSwitch(
            SwitchTriggerPlannerRegistry.PlanningContext context
    ) {
        if (!context.actorActive() || context.actorFainted()) return Optional.empty();
        if (context.availableAp() < QUICK_SWITCH_AP) return Optional.empty();
        if (context.replacementIds().isEmpty()) return Optional.empty();
        if (context.trigger() == SwitchTriggerDecisionPlan.Trigger.ALLY_FAINT
                && context.triggerAlreadyHandled()) {
            return Optional.empty();
        }

        return Optional.of(new SwitchTriggerDecisionPlan(
                QUICK_SWITCH,
                context.trigger(),
                context.actorId(),
                context.replacementIds(),
                context.replacementIds().getFirst(),
                QUICK_SWITCH_AP,
                QUICK_SWITCH_AP,
                "interrupt",
                true,
                new SwitchTriggerDecisionPlan.SwitchPolicy(
                        false,
                        true,
                        false,
                        false
                ),
                QUICK_SWITCH_SENT_OUT,
                context.trigger() == SwitchTriggerDecisionPlan.Trigger.ALLY_FAINT
                        ? QUICK_SWITCH_FAINT_HANDLED
                        : ""
        ));
    }
}
