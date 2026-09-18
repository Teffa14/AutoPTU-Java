package io.autoptu.core.hook;

/**
 * Revalidates a discovered reaction candidate against the current authoritative battle state.
 * Implementations may inspect action economy, active/fainted state, temporary effects, source
 * availability, target legality, or other rule-owned state required by the registered action.
 */
@FunctionalInterface
public interface ActionWindowCommitValidator {
    boolean canCommit(ActionWindowCandidate candidate);
}
