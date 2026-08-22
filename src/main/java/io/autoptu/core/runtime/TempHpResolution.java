package io.autoptu.core.runtime;

import java.util.Objects;

/**
 * Server-authoritative temporary-HP grant primitive shared by Trainer Features,
 * abilities, items, and future move effects.
 *
 * Mirrors Python PokemonState.add_temp_hp(): non-positive grants are ignored,
 * Heal Block/Heal Blocked prevent grants, temp_hp_locked prevents grants, and
 * successful grants stack additively without a max-HP cap.
 */
public final class TempHpResolution {
    private TempHpResolution() {}

    public static int grant(BattleRuntimeState state, String combatantId, int amount) {
        Objects.requireNonNull(state, "state");
        RuntimeCombatantState combatant = state.requireCombatant(combatantId);
        int normalizedAmount = Math.max(0, amount);
        if (normalizedAmount <= 0) return 0;
        if (state.hasStatus(combatantId, "Heal Blocked") || state.hasStatus(combatantId, "Heal Block")) {
            return 0;
        }
        if (combatant.temporaryEffects().has("temp_hp_locked")) return 0;
        return combatant.addTempHpFromRuntime(normalizedAmount);
    }
}
