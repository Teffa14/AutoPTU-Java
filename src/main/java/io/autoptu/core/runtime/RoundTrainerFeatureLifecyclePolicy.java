package io.autoptu.core.runtime;

/**
 * Language-neutral contract frozen from Python PhaseController.start_round().
 *
 * This class deliberately describes ordering and guards only. Trainer Feature
 * execution remains a separate runtime slice so Java does not invent effect
 * semantics before the Python dispatcher/state contracts are ported.
 */
public record RoundTrainerFeatureLifecyclePolicy(
        boolean clearsDeclaredActions,
        boolean declaredActionsAfterTrainerReset,
        boolean declaredActionsBeforeInitialSendout,
        boolean initialSendoutRoundOneOnly,
        boolean initialSendoutRequiresActivePokemon,
        boolean initialSendoutSkipsFaintedPokemon,
        boolean initialSendoutUsesInitialSetup,
        boolean initiativeRebuildBeforeRoundStartEvent,
        boolean roundStartEventBeforeFeatureDispatch,
        boolean dispatchesRoundStartTrigger,
        boolean roundStartPayloadUsesCurrentRound
) {
    public static RoundTrainerFeatureLifecyclePolicy pythonParityContract() {
        return new RoundTrainerFeatureLifecyclePolicy(
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
