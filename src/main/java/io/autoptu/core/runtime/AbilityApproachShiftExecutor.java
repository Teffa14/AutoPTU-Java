package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic server-owned hook family for abilities that cause their active holders to make a legal
 * Shift toward one combatant. Ability identity selects holders; movement legality stays delegated
 * to the canonical Shift resolver.
 */
public final class AbilityApproachShiftExecutor {
    private AbilityApproachShiftExecutor() {
    }

    public static List<ShiftResult> execute(BattleRuntimeState state, String abilityName, String targetId) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (abilityName == null || abilityName.isBlank()) throw new IllegalArgumentException("abilityName is required");
        state.requireCombatant(targetId);

        ArrayList<ShiftResult> results = new ArrayList<>();
        for (String holderId : ActiveAbilityHolderResolver.resolve(state, abilityName)) {
            RuntimeCombatantState holder = state.requireCombatant(holderId);
            GridCoord from = holder.position();
            RuntimeApproachShiftDestinationResolver.closestLegalShiftToward(state, holderId, targetId)
                    .ifPresent(destination -> {
                        holder.moveTo(destination);
                        results.add(new ShiftResult(holderId, abilityName.strip(), from, destination));
                    });
        }
        return List.copyOf(results);
    }

    public record ShiftResult(String actorId, String ability, GridCoord from, GridCoord to) {
        public ShiftResult {
            if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
            if (ability == null || ability.isBlank()) throw new IllegalArgumentException("ability is required");
            if (from == null || to == null) throw new IllegalArgumentException("shift coordinates are required");
        }
    }
}
