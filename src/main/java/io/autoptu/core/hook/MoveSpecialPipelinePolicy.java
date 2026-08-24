package io.autoptu.core.hook;

import java.util.List;

/** Language-neutral contract for the relative ordering of Python move-special phases. */
public record MoveSpecialPipelinePolicy(
        boolean preDamageBeforePostDamage,
        boolean postDamageBeforeEndAction,
        boolean allThreePhasesShareOneResolver) {

    public static MoveSpecialPipelinePolicy pythonOracleContract() {
        return new MoveSpecialPipelinePolicy(true, true, true);
    }

    public List<MoveSpecialPhase> orderedPhases() {
        return List.of(MoveSpecialPhase.PRE_DAMAGE, MoveSpecialPhase.POST_DAMAGE, MoveSpecialPhase.END_ACTION);
    }
}
