package io.autoptu.core.runtime;

/**
 * Server-owned semantic inputs needed to project one Pokemon into the parity-tested
 * initiative-entry pipeline.
 *
 * This record deliberately contains rule inputs rather than a precomputed initiative
 * total. Minecraft/Cobblemon adapters must never calculate the resulting order.
 */
public record RuntimeInitiativePokemonContext(
        int trainerModifier,
        boolean tailwindActive,
        String weather,
        String terrainName,
        boolean grounded,
        boolean agilityTraining,
        boolean riderAgilityTrainingDoubled,
        int hardenedInitiativeBonus,
        boolean parentalBondChild,
        boolean initiativeZeroUntilTurn
) {
    public RuntimeInitiativePokemonContext {
        weather = weather == null ? "" : weather.strip();
        terrainName = terrainName == null ? "" : terrainName.strip();
    }

    public static RuntimeInitiativePokemonContext neutral() {
        return new RuntimeInitiativePokemonContext(
                0, false, "", "", true, false, false, 0, false, false
        );
    }
}
