package io.autoptu.core.hook;

/** Frozen language-neutral contract for Python move-special dispatch. */
public record MoveSpecialDispatchPolicy(
        boolean unknownPhaseDefaultsToPostDamage,
        boolean postDamageRunsSpecificBeforeGlobal,
        boolean otherPhasesRunGlobalBeforeSpecific,
        boolean moveNamesNormalizeTrimLower,
        boolean shieldDustSkipsNonStatusPostDamage,
        boolean shieldDustAllowsStatusPostDamage,
        boolean contextRetainsSharedMutableResult,
        boolean hitIsSnapshotTakenBeforeHandlerMutation
) {
    public static MoveSpecialDispatchPolicy pythonOracleContract() {
        return new MoveSpecialDispatchPolicy(true, true, true, true, true, true, true, true);
    }
}
