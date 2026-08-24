package io.autoptu.core.hook;

/**
 * Language-neutral execution policy for PRE-damage reactions that synchronously re-enter
 * resolution of the original move.
 *
 * <p>The pinned Python oracle calls {@code battle.resolve_move_targets(...)} directly from
 * reactions such as Sway and Magic Coat. That bypasses the ordinary action wrapper, preserves
 * the original move, re-enters the normal target/damage pipeline synchronously, and therefore
 * must not spend action economy or move frequency a second time.</p>
 */
public record PreDamageFollowUpExecutionPolicy(
        boolean synchronous,
        boolean reuseOriginalMove,
        boolean runPreDamageReactions,
        boolean spendAction,
        boolean spendMoveFrequency
) {
    /** Contract frozen from the pinned Python AutoPTU oracle. */
    public static PreDamageFollowUpExecutionPolicy pythonParity() {
        return new PreDamageFollowUpExecutionPolicy(true, true, true, false, false);
    }
}
