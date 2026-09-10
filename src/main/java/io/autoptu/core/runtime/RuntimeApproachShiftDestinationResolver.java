package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.Targeting;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Server-owned policy for choosing a legal Shift destination that approaches another combatant.
 *
 * <p>The legal destination set remains owned by {@link RuntimeShiftDestinationResolver}. This
 * resolver only applies the pinned Python approach policy: the destination must strictly reduce
 * combatant distance, then the closest result wins with x/y as deterministic tie breakers.</p>
 */
public final class RuntimeApproachShiftDestinationResolver {
    private RuntimeApproachShiftDestinationResolver() {
    }

    public static Optional<GridCoord> closestLegalShiftToward(
            BattleRuntimeState state,
            String actorId,
            String targetId
    ) {
        if (state == null) {
            throw new IllegalArgumentException("battle state is required");
        }
        RuntimeCombatantState actor = state.requireCombatant(actorId);
        RuntimeCombatantState target = state.requireCombatant(targetId);
        if (actorId.equals(targetId) || actor.hp() <= 0 || target.hp() <= 0) {
            return Optional.empty();
        }

        Set<GridCoord> reachable = new LinkedHashSet<>(
                RuntimeShiftDestinationResolver.legalShiftTiles(state, actorId, 0)
        );

        // Python Ball Fetch adds this anchor-position occupancy filter after ordinary Shift
        // generation. It includes conscious combatants even when they are not active.
        Set<GridCoord> occupiedAnchors = new LinkedHashSet<>();
        for (String otherId : state.combatantIds()) {
            if (actorId.equals(otherId)) {
                continue;
            }
            RuntimeCombatantState other = state.requireCombatant(otherId);
            if (other.hp() > 0) {
                occupiedAnchors.add(other.position());
            }
        }
        reachable.removeAll(occupiedAnchors);
        if (reachable.isEmpty()) {
            return Optional.empty();
        }

        int currentDistance = combatantDistance(state, actorId, targetId);
        Comparator<GridCoord> pythonOrder = Comparator
                .comparingInt((GridCoord coord) -> combatantDistanceToCoord(state, targetId, coord))
                .thenComparingInt(GridCoord::x)
                .thenComparingInt(GridCoord::y);

        return reachable.stream()
                .filter(coord -> combatantDistanceToCoord(state, targetId, coord) < currentDistance)
                .min(pythonOrder);
    }

    private static int combatantDistance(BattleRuntimeState state, String firstId, String secondId) {
        Set<GridCoord> first = Targeting.footprintTiles(
                state.requireCombatant(firstId).position(),
                state.geometry(firstId).sizeLabel()
        );
        Set<GridCoord> second = Targeting.footprintTiles(
                state.requireCombatant(secondId).position(),
                state.geometry(secondId).sizeLabel()
        );
        return minimumChebyshevDistance(first, second);
    }

    private static int combatantDistanceToCoord(BattleRuntimeState state, String targetId, GridCoord coord) {
        Set<GridCoord> target = Targeting.footprintTiles(
                state.requireCombatant(targetId).position(),
                state.geometry(targetId).sizeLabel()
        );
        return minimumChebyshevDistance(target, Set.of(coord));
    }

    private static int minimumChebyshevDistance(Set<GridCoord> first, Set<GridCoord> second) {
        int best = Integer.MAX_VALUE;
        for (GridCoord left : first) {
            for (GridCoord right : second) {
                best = Math.min(best, Math.max(
                        Math.abs(left.x() - right.x()),
                        Math.abs(left.y() - right.y())
                ));
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }
}
