package io.autoptu.core.runtime;

/**
 * Language-neutral execution contract for non-damaging Status moves.
 *
 * <p>The pinned Python oracle still routes Status moves through ordinary move accuracy, then
 * returns a zero-damage result rather than entering the physical/special damage arithmetic.
 * This contract intentionally freezes only that boundary. Status effect application remains a
 * separate concern and must be ported from the Python move-special/effect pipeline.</p>
 */
public record StatusMoveExecutionPolicy(
        boolean statusBranchPresent,
        boolean hitComesFromAccuracyResult,
        boolean critIsAlwaysFalse,
        boolean damageIsAlwaysZero,
        boolean damageRollIsAlwaysZero
) {
    public static StatusMoveExecutionPolicy pythonOracleContract() {
        return new StatusMoveExecutionPolicy(true, true, true, true, true);
    }
}
