package io.autoptu.core.runtime;

/**
 * Controls resource bookkeeping around one authoritative move resolution.
 *
 * Ordinary submitted moves validate and consume action/frequency resources. Some
 * server-owned effects, such as a delayed hit maturing on a later round, re-enter
 * the same PTU move-resolution pipeline after those resources were already paid at
 * scheduling time. Those effects must not create a second action/frequency spend.
 */
public record MoveExecutionPolicy(
        boolean validateOrdinaryLegality,
        boolean consumeAction,
        boolean recordFrequencyUse
) {
    public static final MoveExecutionPolicy ORDINARY = new MoveExecutionPolicy(true, true, true);
    public static final MoveExecutionPolicy DELAYED_TRIGGER = new MoveExecutionPolicy(false, false, false);
}
