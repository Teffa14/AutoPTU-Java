package io.autoptu.core.hook;

/**
 * Language-neutral contract for Magic Coat reflection at the PRE-damage boundary.
 *
 * <p>The pinned Python AutoPTU oracle only reflects Status-category moves, excludes Magic Coat
 * itself, consumes the defender's Magic Coat status before synchronously re-entering target
 * resolution with the original move, swaps the defender into the attacker role, targets the
 * original attacker at its live position when available, swallows nested-resolution errors, and
 * marks the original result as shield-blocked with zero damage and type multiplier.</p>
 */
public record MagicCoatReflectionPolicy(
        boolean statusCategoryOnly,
        boolean excludeMagicCoatMove,
        boolean requireMagicCoatStatus,
        boolean consumeMagicCoatBeforeFollowUp,
        boolean synchronousFollowUp,
        boolean reuseOriginalMove,
        boolean defenderBecomesAttacker,
        boolean originalAttackerBecomesTarget,
        boolean useOriginalAttackerLivePosition,
        boolean swallowFollowUpErrors,
        boolean zeroDamage,
        boolean zeroTypeMultiplier,
        boolean markBlockedByShield
) {
    /** Contract frozen from the pinned Python AutoPTU oracle. */
    public static MagicCoatReflectionPolicy pythonParity() {
        return new MagicCoatReflectionPolicy(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true
        );
    }
}
