package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.LifecycleHookRegistry;
import io.autoptu.core.hook.SwitchTriggerDecisionPlan;

import java.util.function.Consumer;

/**
 * Server-authoritative bridge from an adapter interrupt response to canonical switch execution.
 *
 * <p>The response resolver owns Python-parity acceptance, replacement legality, and fallback.
 * Rejected responses stop without mutating battle state. Accepted responses are delegated to the
 * canonical switch-trigger executor, which revalidates live state before spending AP or applying
 * the switch. Adapters therefore submit only the neutral response envelope and never perform PTU
 * legality, AP, or state transitions themselves.</p>
 */
public final class SwitchTriggerInterruptCoordinator {
    private SwitchTriggerInterruptCoordinator() {
    }

    public static Outcome resolveAndExecute(
            BattleRuntimeState state,
            CombatantFieldPresenceStore fieldPresence,
            LifecycleHookRegistry lifecycleHooks,
            Consumer<BattleEvent> eventSink,
            SwitchTriggerDecisionPlan plan,
            SwitchTriggerInterruptResponseResolver.Response response
    ) {
        SwitchTriggerInterruptResponseResolver.Resolution resolution =
                SwitchTriggerInterruptResponseResolver.resolve(plan, response);
        if (!resolution.accepted()) {
            return Outcome.rejected(resolution);
        }

        SwitchTriggerDecisionExecutor.ExecutionResult execution = SwitchTriggerDecisionExecutor.execute(
                state,
                fieldPresence,
                lifecycleHooks,
                eventSink,
                resolution.plan(),
                resolution.replacementId()
        );
        return Outcome.executed(resolution, execution);
    }

    public record Outcome(
            SwitchTriggerInterruptResponseResolver.Resolution resolution,
            SwitchTriggerDecisionExecutor.ExecutionResult executionResult
    ) {
        public Outcome {
            if (resolution == null) throw new IllegalArgumentException("interrupt resolution is required");
            if (resolution.accepted() && executionResult == null) {
                throw new IllegalArgumentException("accepted interrupt requires execution result");
            }
            if (!resolution.accepted() && executionResult != null) {
                throw new IllegalArgumentException("rejected interrupt cannot have execution result");
            }
        }

        public boolean accepted() {
            return resolution.accepted();
        }

        static Outcome rejected(SwitchTriggerInterruptResponseResolver.Resolution resolution) {
            return new Outcome(resolution, null);
        }

        static Outcome executed(
                SwitchTriggerInterruptResponseResolver.Resolution resolution,
                SwitchTriggerDecisionExecutor.ExecutionResult executionResult
        ) {
            return new Outcome(resolution, executionResult);
        }
    }
}
