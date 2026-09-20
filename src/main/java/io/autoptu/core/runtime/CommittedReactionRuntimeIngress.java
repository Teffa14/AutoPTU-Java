package io.autoptu.core.runtime;

import java.util.Objects;

/**
 * Single handoff boundary between committed reaction validation and authoritative move resolution.
 * The reaction window owns declaration validation and reaction resource spending. The downstream
 * move resolver owns accuracy, damage, RNG, hooks, HP/history mutation, and semantic events.
 */
public final class CommittedReactionRuntimeIngress {
    private CommittedReactionRuntimeIngress() {}

    public record Dispatch(MoveRuntimeExecutionMode executionMode) {
        public Dispatch {
            Objects.requireNonNull(executionMode, "executionMode");
        }

        public boolean spendOrdinaryMoveResources() {
            return executionMode.spendOrdinaryMoveResources();
        }

        public boolean runPreDamageReactions() {
            return executionMode.runPreDamageReactions();
        }

        public boolean declaredChoiceAlreadyValidated() {
            return executionMode.declarationAlreadyValidated();
        }
    }

    @FunctionalInterface
    public interface Resolver<R> {
        R resolve(CommittedReactionRuntimeExecutionPlan plan, MoveRuntimeExecutionMode executionMode);
    }

    public static Dispatch dispatch(CommittedReactionRuntimeExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan is required");
        if (!plan.declarationAlreadyValidated()) {
            throw new IllegalArgumentException("committed reaction declaration must already be validated");
        }
        if (plan.spendOrdinaryMoveResources()) {
            throw new IllegalArgumentException("committed reaction must not spend ordinary move resources twice");
        }
        return new Dispatch(MoveRuntimeExecutionMode.COMMITTED_REACTION);
    }

    public static <R> R resolve(
            CommittedReactionRuntimeExecutionPlan plan,
            Resolver<R> resolver
    ) {
        Objects.requireNonNull(resolver, "resolver is required");
        Dispatch dispatch = dispatch(plan);
        return resolver.resolve(plan, dispatch.executionMode());
    }
}
