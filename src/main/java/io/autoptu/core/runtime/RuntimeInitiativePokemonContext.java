package io.autoptu.core.runtime;

/**
 * Legacy compatibility inputs for callers compiled against older initiative boundaries.
 *
 * All PTU initiative inputs are now derived from BattleRuntimeState by the preferred
 * RuntimeInitiativePokemonCandidateFactory boundary. These fields are retained only to
 * preserve source compatibility while callers migrate; they are not authoritative.
 */
@Deprecated
public record RuntimeInitiativePokemonContext(
        int trainerModifier,
        boolean agilityTraining,
        boolean riderAgilityTrainingDoubled,
        int hardenedInitiativeBonus,
        boolean parentalBondChild,
        boolean initiativeZeroUntilTurn
) {
    /**
     * Transitional constructor for callers compiled against the pre-environment-state
     * boundary. Every argument is compatibility-only and ignored by the preferred runtime path.
     */
    @Deprecated
    public RuntimeInitiativePokemonContext(
            int trainerModifier,
            boolean ignoredTailwindActive,
            String ignoredWeather,
            String ignoredTerrainName,
            boolean ignoredGrounded,
            boolean agilityTraining,
            boolean riderAgilityTrainingDoubled,
            int hardenedInitiativeBonus,
            boolean parentalBondChild,
            boolean initiativeZeroUntilTurn
    ) {
        this(
                trainerModifier,
                agilityTraining,
                riderAgilityTrainingDoubled,
                hardenedInitiativeBonus,
                parentalBondChild,
                initiativeZeroUntilTurn
        );
    }

    public static RuntimeInitiativePokemonContext neutral() {
        return new RuntimeInitiativePokemonContext(0, false, false, 0, false, false);
    }
}
