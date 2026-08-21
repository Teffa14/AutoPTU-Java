package io.autoptu.core.runtime;

/**
 * Remaining server-owned semantic inputs needed to project one Pokemon into the
 * parity-tested initiative-entry pipeline.
 *
 * Weather, terrain, Tailwind and grounded state are intentionally absent from the
 * canonical record because they belong to BattleRuntimeState. Minecraft/Cobblemon
 * adapters must never calculate the resulting initiative order.
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
