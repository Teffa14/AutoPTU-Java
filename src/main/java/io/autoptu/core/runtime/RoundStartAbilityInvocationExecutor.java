package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ordered executor between the Python-parity round-start plan and ability effect handlers. */
public final class RoundStartAbilityInvocationExecutor {
    private RoundStartAbilityInvocationExecutor() {}

    public record InvocationResult(
            RoundStartAbilityDispatchPlan.Invocation invocation,
            boolean handled,
            List<BattleEvent> events
    ) {
        public InvocationResult {
            invocation = Objects.requireNonNull(invocation, "invocation");
            events = events == null ? List.of() : List.copyOf(events);
            if (!handled && !events.isEmpty()) {
                throw new IllegalArgumentException("unhandled invocation cannot emit events");
            }
        }
    }

    public static List<InvocationResult> execute(
            List<RoundStartAbilityDispatchPlan.Invocation> invocations,
            BattleRuntimeState state,
            RoundStartAbilityEffectRegistry registry
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(registry, "registry");
        ArrayList<InvocationResult> results = new ArrayList<>();
        for (RoundStartAbilityDispatchPlan.Invocation invocation :
                invocations == null ? List.<RoundStartAbilityDispatchPlan.Invocation>of() : invocations) {
            if (invocation == null) throw new IllegalArgumentException("invocation is required");
            boolean handled = registry.supports(invocation.family());
            List<BattleEvent> events = handled ? registry.apply(invocation, state) : List.of();
            results.add(new InvocationResult(invocation, handled, events));
        }
        return List.copyOf(results);
    }
}
