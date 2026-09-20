package io.autoptu.core.runtime;

import java.util.Objects;

/**
 * Single handoff boundary between committed reaction validation and authoritative move resolution.
 * The reaction window owns declaration validation and reaction resource spending. The downstream
 * move resolver owns accuracy, damage, RNG, hooks, HP/history mutation, and semantic events.
 */
public final class CommittedReactionRuntimeIngress {
    private CommittedReactionRuntimeIngress() {}

    /**
     * Explicit execution ownership for the downstream BattleRuntime dispatcher. Keeping this as a
     * named mode prevents committed reactions from being inferred as AoE merely because ordinary
     * move resources are not spent a second time.
     */
    public enum ExecutionMode {
        COMMITTED_REACTION
    }

    public record Dispatch(
            ExecutionMode executionMode,
            boolean spendOrdinaryMoveResources,
            boolean runPreDamageReactions,
            boolean declaredChoiceAlreadyValidated
    ) {
        public Dispatch {
            Objects.requireNonNull(executionMode, "executionMode");
        }
    }

    @FunctionalInterface
    public interface Resolver<R> {
        R resolve(
                CommittedReactionRuntimeExecutionPlan plan,
                boolean spendOrdinaryMoveResources,
                boolean runPreDamageReactions,
                boolean declaredChoiceAlreadyValidated
        );
    }

    public static Dispatch dispatch(CommittedReactionRuntimeExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan is required");
        if (!plan.declarationAlreadyValidated()) {
            throw new IllegalArgumentException("committed reaction declaration must already be validated");
        }
        if (plan.spendOrdinaryMoveResources()) {
            throw new IllegalArgumentException("committed reaction must not spend ordinary move resources twice");
        }
        return new Dispatch(ExecutionMode.COMMITTED_REACTION, false, true, true);
    }

    public static <R> R resolve(
            CommittedReactionRuntimeExecutionPlan plan,
            Resolver<R> resolver
    ) {
        Objects.requireNonNull(resolver, "resolver is required");
        Dispatch dispatch = dispatch(plan);
        return resolver.resolve(
                plan,
                dispatch.spendOrdinaryMoveResources(),
                dispatch.runPreDamageReactions(),
                dispatch.declaredChoiceAlreadyValidated()
        );
    }
}
