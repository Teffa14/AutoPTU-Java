package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Executes StatusController phase-envelope steps in the Python-oracle order.
 *
 * <p>Concrete held-item, food and combatant phase behavior remains in separate
 * lifecycle hooks. This dispatcher owns only cross-family ordering and the
 * StatusController rule that pending status skips originate from Pokemon phase
 * effects rather than the surrounding held-item/food envelope.</p>
 */
public final class StatusControllerPhaseEnvelopeDispatcher implements LifecycleHook {
    private final Map<StatusControllerPhaseOrderingPolicy.Step, LifecycleHook> steps;

    private StatusControllerPhaseEnvelopeDispatcher(
            Map<StatusControllerPhaseOrderingPolicy.Step, LifecycleHook> steps
    ) {
        this.steps = Map.copyOf(steps);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        Objects.requireNonNull(context, "context");
        if (context.phase() == null) {
            throw new IllegalArgumentException("phase is required for StatusController phase envelope");
        }

        List<BattleEvent> events = new ArrayList<>();
        PendingStatusSkipRequest pendingStatusSkip = null;

        for (StatusControllerPhaseOrderingPolicy.Step step
                : StatusControllerPhaseOrderingPolicy.sequence(context.phase())) {
            LifecycleHook hook = steps.get(step);
            if (hook == null) {
                continue;
            }
            LifecycleHookResult result = Objects.requireNonNull(hook.apply(context), "phase step hook result");
            events.addAll(result.events());
            if (step == StatusControllerPhaseOrderingPolicy.Step.COMBATANT_PHASE_EFFECTS
                    && result.pendingStatusSkip() != null) {
                pendingStatusSkip = result.pendingStatusSkip();
            }
        }

        return new LifecycleHookResult(events, pendingStatusSkip);
    }

    public static final class Builder {
        private final EnumMap<StatusControllerPhaseOrderingPolicy.Step, LifecycleHook> steps =
                new EnumMap<>(StatusControllerPhaseOrderingPolicy.Step.class);

        public Builder step(StatusControllerPhaseOrderingPolicy.Step step, LifecycleHook hook) {
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(hook, "hook");
            if (steps.putIfAbsent(step, hook) != null) {
                throw new IllegalArgumentException("duplicate StatusController phase step: " + step);
            }
            return this;
        }

        public StatusControllerPhaseEnvelopeDispatcher build() {
            return new StatusControllerPhaseEnvelopeDispatcher(steps);
        }
    }
}
