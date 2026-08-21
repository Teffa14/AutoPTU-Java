package io.autoptu.core.runtime;

/**
 * Resource-bookkeeping contract for a delayed hit when it matures.
 *
 * <p>The originating move declaration owns action and frequency bookkeeping. The pinned
 * Python oracle later routes the scheduled hit directly through target/attack resolution,
 * so maturity must not spend those resources again.</p>
 */
public record DelayedHitResourcePolicy(
        boolean entersTargetResolution,
        boolean spendsActionAtMaturity,
        boolean consumesFrequencyAtMaturity,
        boolean recordsNormalMoveUseAtMaturity,
        boolean resolvesAttackAtMaturity
) {
    public static DelayedHitResourcePolicy pythonOracle() {
        return new DelayedHitResourcePolicy(true, false, false, false, true);
    }
}
