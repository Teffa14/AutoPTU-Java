package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic server-side spatial lookup for ability aura/reaction sources.
 *
 * Results preserve canonical battle insertion order. Minecraft entity proximity is
 * never queried here; positions, HP, active state, team and abilities all come from
 * BattleRuntimeState.
 */
public final class SpatialAbilityQuery {
    private SpatialAbilityQuery() {}

    public static List<String> holdersInRadius(
            BattleRuntimeState state,
            GridCoord origin,
            String abilityName,
            int radius
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(origin, "origin");
        if (abilityName == null || abilityName.isBlank()) {
            throw new IllegalArgumentException("abilityName is required");
        }
        if (radius < 0) {
            throw new IllegalArgumentException("radius cannot be negative");
        }

        ArrayList<String> matches = new ArrayList<>();
        for (String combatantId : state.combatantIds()) {
            RuntimeCombatantState combatant = state.requireCombatant(combatantId);
            if (!state.isActive(combatantId) || combatant.hp() <= 0) continue;
            if (!combatant.hasAbilityExact(abilityName)) continue;
            if (chebyshevDistance(origin, combatant.position()) > radius) continue;
            matches.add(combatantId);
        }
        return List.copyOf(matches);
    }

    public static int chebyshevDistance(GridCoord first, GridCoord second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return Math.max(Math.abs(first.x() - second.x()), Math.abs(first.y() - second.y()));
    }
}
