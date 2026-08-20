package io.autoptu.core.rules;

import java.util.List;

/**
 * Pure parity boundary for initiative bonuses applied after initiative-time Speed resolution.
 *
 * This resolver mirrors the Early Bird [Errata], Agility Training/rider doubling and
 * Hardened Initiative contributions inside Python BattleState._initiative_entry_for_pokemon().
 * All inputs are authoritative semantic state owned by the Java battle core; Minecraft/Cobblemon
 * must not compute the resulting initiative bonus.
 */
public final class InitiativeAdditionalBonusResolution {
    private InitiativeAdditionalBonusResolution() {
    }

    public static int resolve(
            int resolvedSpeed,
            List<String> abilities,
            boolean agilityTraining,
            boolean riderAgilityTrainingDoubled,
            int hardenedInitiativeBonus
    ) {
        int bonus = 0;

        if (AbilityIdentityResolution.matchesExact(abilities, "Early Bird [Errata]")) {
            bonus += Math.max(0, resolvedSpeed / 2);
        }

        if (agilityTraining) {
            bonus += 4;
            if (riderAgilityTrainingDoubled) {
                bonus += 4;
            }
        }

        bonus += hardenedInitiativeBonus;
        return bonus;
    }
}
