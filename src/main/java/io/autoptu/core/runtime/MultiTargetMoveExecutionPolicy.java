package io.autoptu.core.runtime;

/**
 * Language-neutral ownership contract for an ordinary move that resolves more than one target.
 *
 * <p>The pinned Python oracle resolves effective targets sequentially inside
 * {@code resolve_move_targets}. Action economy and move-frequency bookkeeping belong to the
 * ordinary action wrapper, not to that per-target loop. Java must preserve that split when TILE
 * choices are wired into damage resolution so an area move spends its declaration resources once
 * while each affected combatant receives its own attack resolution.</p>
 */
public record MultiTargetMoveExecutionPolicy(
        boolean resolvesEachTargetInsideTargetLoop,
        boolean targetLoopMarksAction,
        boolean targetLoopRecordsMoveFrequency,
        boolean targetLoopRecordsMoveUsed,
        boolean ordinaryActionMarksAction,
        boolean ordinaryMoveChecksFrequency,
        boolean ordinaryMoveRecordsFrequency,
        boolean ordinaryMoveRecordsMoveUsed
) {
    public static MultiTargetMoveExecutionPolicy pythonOracleContract() {
        return new MultiTargetMoveExecutionPolicy(
                true,
                false,
                false,
                false,
                true,
                true,
                true,
                true
        );
    }
}
