package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.Movement;

import java.util.Set;

/** Generic server-owned Shift destination resolver shared by AI and movement reactions. */
public final class RuntimeShiftDestinationResolver {
    private RuntimeShiftDestinationResolver() {
    }

    public static Set<GridCoord> legalShiftTiles(
            BattleRuntimeState state,
            String actorId,
            int limitPenalty
    ) {
        if (state == null) {
            throw new IllegalArgumentException("battle state is required");
        }
        RuntimeCombatantState actor = state.requireCombatant(actorId);
        return Movement.legalShiftTiles(
                state.grid(),
                actor.movementProfile(),
                limitPenalty,
                destination -> RuntimePositionFitResolver.canFit(state, actorId, destination)
        );
    }
}
