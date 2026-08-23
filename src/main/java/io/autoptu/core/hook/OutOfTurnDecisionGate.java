package io.autoptu.core.hook;

/** Server-owned decision boundary for optional out-of-turn reactions. */
@FunctionalInterface
public interface OutOfTurnDecisionGate {
    boolean shouldTrigger(OutOfTurnDecisionRequest request);

    /** Python parity: if no decision callback exists, optional reactions are allowed. */
    static OutOfTurnDecisionGate allowWhenUnconfigured() {
        return request -> true;
    }
}
