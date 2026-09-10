package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.List;

/** Resolves active, conscious holders of one effective ability in battle insertion order. */
public final class ActiveAbilityHolderResolver {
    private ActiveAbilityHolderResolver() {}

    public static List<String> resolve(BattleRuntimeState state, String abilityName) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (abilityName == null || abilityName.isBlank()) {
            throw new IllegalArgumentException("abilityName is required");
        }
        ArrayList<String> holders = new ArrayList<>();
        for (String combatantId : state.combatantIds()) {
            RuntimeCombatantState combatant = state.requireCombatant(combatantId);
            if (!state.isActive(combatantId)) continue;
            if (combatant.hp() <= 0) continue;
            if (!EffectiveAbilityResolver.hasExact(combatant, abilityName)) continue;
            holders.add(combatantId);
        }
        return List.copyOf(holders);
    }
}
