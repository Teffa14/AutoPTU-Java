package io.autoptu.core.runtime;

import io.autoptu.core.rules.ActionEconomyProfile;

import java.util.Collection;

/**
 * Resolves the single action-economy profile that owns one authoritative battle.
 *
 * <p>Compatibility battles default to the pinned Python oracle profile when no
 * combatants are present. Mixed profiles are rejected so legal-action generation,
 * move execution, Shift execution, and future hooks cannot interpret the same battle
 * with different action-resource semantics.</p>
 */
public final class BattleActionEconomyProfileResolver {
    private BattleActionEconomyProfileResolver() {}

    public static ActionEconomyProfile resolve(Collection<RuntimeCombatantState> combatants) {
        if (combatants == null || combatants.isEmpty()) {
            return ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY;
        }

        ActionEconomyProfile resolved = null;
        for (RuntimeCombatantState combatant : combatants) {
            if (combatant == null) {
                continue;
            }
            ActionEconomyProfile candidate = combatant.actionBudget().profile();
            if (resolved == null) {
                resolved = candidate;
                continue;
            }
            if (candidate != resolved) {
                throw new IllegalArgumentException(
                        "mixed action economy profiles are not allowed in one battle: "
                                + resolved + " vs " + candidate
                );
            }
        }

        return resolved == null
                ? ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY
                : resolved;
    }
}
