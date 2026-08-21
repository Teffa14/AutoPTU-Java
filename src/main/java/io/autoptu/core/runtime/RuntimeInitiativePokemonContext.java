package io.autoptu.core.runtime;

/**
 * Remaining semantic compatibility inputs for projecting one Pokemon into the
 * parity-tested initiative-entry pipeline.
 *
 * Weather, terrain, Tailwind, grounded state, Trainer modifier, Agility Training,
 * Hardened Initiative, Parental Bond child state, and initiative-zero state are all
 * derived from BattleRuntimeState. Minecraft/Cobblemon adapters must never calculate
 * those results. Rider Agility Training doubling remains the one authoritative field
 * here until mount/rider relationships are represented canonically.
 */
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
     * boundary. Environment arguments are deliberately ignored; the runtime reads the
     * canonical BattleEnvironmentState instead.
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
