package io.autoptu.core.runtime;

import java.util.Objects;

/**
 * Single handoff boundary between committed reaction validation and authoritative move resolution.
 * The reaction window owns declaration validation and reaction resource spending. The downstream
 * move resolver owns accuracy, damage, RNG, hooks, HP/history mutation, and semantic events.
 */
public final class CommittedReactionRuntimeIngress {
    private CommittedReactionRuntimeIngress() {}

    @FunctionalInterface
    public interface Resolver<R> {
        R resolve(
                CommittedReactionRuntimeExecutionPlan plan,
                boolean spendOrdinaryMoveResources,
                boolean runPreDamageReactions,
                boolean declaredChoiceAlreadyValidated
        );
    }

    public static <R> R resolve(
            CommittedReactionRuntimeExecutionPlan plan,
            Resolver<R> resolver
    ) {
        Objects.requireNonNull(plan, "plan is required");
        Objects.requireNonNull(resolver, "resolver is required");
        if (!plan.declarationAlreadyValidated()) {
            throw new IllegalArgumentException("committed reaction declaration must already be validated");
        }
        if (plan.spendOrdinaryMoveResources()) {
            throw new IllegalArgumentException("committed reaction must not spend ordinary move resources twice");
        }
        return resolver.resolve(plan, false, true, true);
    }
}
