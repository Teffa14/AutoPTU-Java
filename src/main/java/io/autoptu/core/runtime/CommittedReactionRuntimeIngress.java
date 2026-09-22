package io.autoptu.core.runtime;

import java.util.Objects;

/**
 * Single handoff boundary between committed reaction validation and authoritative move resolution.
 * The reaction window owns declaration validation and reaction resource spending. The downstream
 * move resolver owns accuracy, damage, RNG, hooks, HP/history mutation, and semantic events.
 */
public final class CommittedReactionRuntimeIngress {
    private CommittedReactionRuntimeIngress() {}

    public record Dispatch(MoveRuntimeExecutionContext executionContext) {
        public Dispatch {
            Objects.requireNonNull(executionContext, "executionContext");
        }

        /** Source-compatible constructor for callers that still provide only execution identity. */
        public Dispatch(MoveRuntimeExecutionMode executionMode) {
            this(MoveRuntimeExecutionContext.of(Objects.requireNonNull(executionMode, "executionMode")));
        }

        /**
         * Source-compatible constructor for callers that still spell out the historical ownership
         * tuple. The tuple may only describe the supplied execution mode; execution identity remains
         * authoritative.
         */
        public Dispatch(
                MoveRuntimeExecutionMode executionMode,
                boolean spendOrdinaryMoveResources,
                boolean runPreDamageReactions,
                boolean declaredChoiceAlreadyValidated
        ) {
            this(executionMode);
            if (spendOrdinaryMoveResources != executionContext.spendOrdinaryMoveResources()
                    || runPreDamageReactions != executionContext.runPreDamageReactions()
                    || declaredChoiceAlreadyValidated != executionContext.declarationAlreadyValidated()) {
                throw new IllegalArgumentException("ownership tuple must match execution mode");
            }
        }

        public MoveRuntimeExecutionMode executionMode() {
            return executionContext.mode();
        }

        public boolean spendOrdinaryMoveResources() {
            return executionContext.spendOrdinaryMoveResources();
        }

        public boolean runPreDamageReactions() {
            return executionContext.runPreDamageReactions();
        }

        public boolean declaredChoiceAlreadyValidated() {
            return executionContext.declarationAlreadyValidated();
        }
    }

    /**
     * Existing source-compatible resolver boundary. Ownership values are projected from the single
     * execution-context identity; callers must not reconstruct execution identity from this tuple.
     */
    @FunctionalInterface
    public interface Resolver<R> {
        R resolve(
                CommittedReactionRuntimeExecutionPlan plan,
                boolean spendOrdinaryMoveResources,
                boolean runPreDamageReactions,
                boolean declaredChoiceAlreadyValidated
        );
    }

    /** Source-compatible resolver boundary for callers that still consume only execution mode. */
    @FunctionalInterface
    public interface ExecutionModeResolver<R> {
        R resolve(CommittedReactionRuntimeExecutionPlan plan, MoveRuntimeExecutionMode executionMode);
    }

    /** Authoritative resolver boundary for new runtime wiring. */
    @FunctionalInterface
    public interface ExecutionContextResolver<R> {
        R resolve(CommittedReactionRuntimeExecutionPlan plan, MoveRuntimeExecutionContext executionContext);
    }

    public static Dispatch dispatch(CommittedReactionRuntimeExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan is required");
        MoveRuntimeExecutionContext executionContext = plan.executionContext();
        if (executionContext.mode() != MoveRuntimeExecutionMode.COMMITTED_REACTION) {
            throw new IllegalArgumentException("committed reaction plan must use COMMITTED_REACTION execution context");
        }
        if (!executionContext.declarationAlreadyValidated()) {
            throw new IllegalArgumentException("committed reaction declaration must already be validated");
        }
        if (executionContext.spendOrdinaryMoveResources()) {
            throw new IllegalArgumentException("committed reaction must not spend ordinary move resources twice");
        }
        return new Dispatch(executionContext);
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

    public static <R> R resolveExecutionMode(
            CommittedReactionRuntimeExecutionPlan plan,
            ExecutionModeResolver<R> resolver
    ) {
        Objects.requireNonNull(resolver, "resolver is required");
        Dispatch dispatch = dispatch(plan);
        return resolver.resolve(plan, dispatch.executionMode());
    }

    public static <R> R resolveExecutionContext(
            CommittedReactionRuntimeExecutionPlan plan,
            ExecutionContextResolver<R> resolver
    ) {
        Objects.requireNonNull(resolver, "resolver is required");
        Dispatch dispatch = dispatch(plan);
        return resolver.resolve(plan, dispatch.executionContext());
    }
}
