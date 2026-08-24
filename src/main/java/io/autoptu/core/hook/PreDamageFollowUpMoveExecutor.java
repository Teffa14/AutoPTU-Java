package io.autoptu.core.hook;

/** Runtime-owned synchronous callback used by PRE-damage reactions that re-enter move resolution. */
@FunctionalInterface
public interface PreDamageFollowUpMoveExecutor {
    PreDamageFollowUpMoveResult execute(PreDamageFollowUpMoveRequest request);

    static PreDamageFollowUpMoveExecutor unavailable() {
        return PreDamageFollowUpMoveExecutionScope::executeCurrent;
    }
}
