package io.autoptu.core.model;

/** Result of one resolved PTU accuracy check. */
public record AccuracyResult(
        boolean hit,
        boolean crit,
        int roll,
        int needed
) {
}
