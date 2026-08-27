package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.rules.Targeting;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-authoritative stepwise forced displacement with collision and partial-stop semantics.
 *
 * <p>This primitive is intentionally independent from Shift/action economy. A caller supplies a
 * one-cell direction and requested distance; the resolver advances one anchor at a time and stops
 * at the last legal position when bounds, blockers, or another living combatant footprint prevent
 * the next step.</p>
 */
public final class ForcedDisplacementResolution {
    private ForcedDisplacementResolution() {}

    public record Result(
            GridCoord origin,
            GridCoord destination,
            int requestedDistance,
            int movedDistance,
            boolean stoppedEarly,
            List<GridCoord> traversedAnchors
    ) {}

    public static Result resolve(
            BattleRuntimeState state,
            String combatantId,
            GridCoord direction,
            int distance
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (combatantId == null || combatantId.isBlank()) throw new IllegalArgumentException("combatantId is required");
        if (direction == null) throw new IllegalArgumentException("direction is required");
        if (distance < 0) throw new IllegalArgumentException("distance cannot be negative");

        int dx = Integer.compare(direction.x(), 0);
        int dy = Integer.compare(direction.y(), 0);
        if (distance > 0 && dx == 0 && dy == 0) {
            throw new IllegalArgumentException("non-zero direction is required for movement");
        }

        RuntimeCombatantState combatant = state.requireCombatant(combatantId);
        GridCoord origin = combatant.position();
        GridCoord current = origin;
        java.util.ArrayList<GridCoord> traversed = new java.util.ArrayList<>();

        for (int step = 0; step < distance; step++) {
            GridCoord candidate = new GridCoord(current.x() + dx, current.y() + dy);
            if (!canOccupy(state, combatantId, candidate)) {
                break;
            }
            current = candidate;
            traversed.add(current);
        }

        return new Result(
                origin,
                current,
                distance,
                traversed.size(),
                traversed.size() < distance,
                List.copyOf(traversed)
        );
    }

    private static boolean canOccupy(BattleRuntimeState state, String movingId, GridCoord anchor) {
        MovementGrid grid = state.grid();
        String movingSize = state.geometry(movingId).sizeLabel();
        Set<GridCoord> candidateFootprint = Targeting.footprintTiles(anchor, movingSize);

        for (GridCoord tile : candidateFootprint) {
            if (!grid.inBounds(tile) || grid.isBlocker(tile)) return false;
        }

        Set<GridCoord> occupied = new LinkedHashSet<>();
        for (String otherId : state.combatantIds()) {
            if (otherId.equals(movingId)) continue;
            RuntimeCombatantState other = state.requireCombatant(otherId);
            if (other.hp() <= 0) continue;
            occupied.addAll(Targeting.footprintTiles(other.position(), state.geometry(otherId).sizeLabel()));
        }

        for (GridCoord tile : candidateFootprint) {
            if (occupied.contains(tile)) return false;
        }
        return true;
    }
}
