package io.autoptu.core.hook;

/**
 * Pure eligibility contract for the PTU Analytic post-result damage bonus.
 *
 * Python treats a defender as having acted when either its action history is non-empty,
 * or the current initiative cursor has already passed that defender's entry. The live
 * runtime can bind those observations from server-owned battle state without allowing
 * Minecraft/Cobblemon to declare whether Analytic applies.
 */
public record AnalyticResolution(boolean defenderActed, int damageBonus) {
    public static AnalyticResolution resolve(
            boolean damagingMove,
            boolean defenderHasActionsTaken,
            int initiativeIndex,
            int defenderInitiativeIndex
    ) {
        if (!damagingMove) {
            return new AnalyticResolution(false, 0);
        }

        boolean acted = defenderHasActionsTaken;
        if (!acted && initiativeIndex >= 0 && defenderInitiativeIndex >= 0) {
            acted = initiativeIndex > defenderInitiativeIndex;
        }
        return new AnalyticResolution(acted, acted ? 5 : 0);
    }
}
