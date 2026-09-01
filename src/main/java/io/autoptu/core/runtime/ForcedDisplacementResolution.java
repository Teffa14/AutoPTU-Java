package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.rules.ForcedMovementStepConstraintResolution;
import io.autoptu.core.rules.Targeting;

import java.util.List;
import java.util.Set;

/**
 * Server-authoritative stepwise forced displacement with collision and partial-stop semantics.
 *
 * <p>This primitive is intentionally independent from Shift/action economy. A caller supplies a
 * one-cell direction and requested distance; the resolver advances one anchor at a time and stops
 * at the last legal position when bounds, blockers, another living combatant footprint, or an
 * active rule constraint prevents the next step.</p>
 */
public final class ForcedDisplacementResolution {
    private ForcedDisplacementResolution() {}

    public enum StopReason {
        NONE,
        OUT_OF_BOUNDS,
        BLOCKER,
        OCCUPIED,
        STEP_CONSTRAINT
    }

    public record Stop(
            StopReason reason,
            GridCoord attemptedAnchor,
            GridCoord blockingTile,
            String blockingCombatantId
    ) {
        public Stop {
            if (reason == null) throw new IllegalArgumentException("stop reason is required");
            if (reason == StopReason.NONE) {
                attemptedAnchor = null;
                blockingTile = null;
                blockingCombatantId = null;
            }
        }

        static Stop none() {
            return new Stop(StopReason.NONE, null, null, null);
        }
    }

    public record Result(
            GridCoord origin,
            GridCoord destination,
            int requestedDistance,
            int movedDistance,
            boolean stoppedEarly,
            List<GridCoord> traversedAnchors,
            Stop stop
    ) {
        public Result {
            if (stop == null) stop = Stop.none();
        }
    }

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
        String movingSize = state.geometry(combatantId).sizeLabel();
        List<ForcedMovementStepConstraintResolution.Constraint> constraints =
                RuntimeForcedMovementStepConstraintProjection.constraints(state, combatantId);
        java.util.ArrayList<GridCoord> traversed = new java.util.ArrayList<>();
        Stop stop = Stop.none();

        for (int step = 0; step < distance; step++) {
            GridCoord candidate = new GridCoord(current.x() + dx, current.y() + dy);
            Stop candidateStop = stopFor(state, combatantId, candidate);
            if (candidateStop.reason() != StopReason.NONE) {
                stop = candidateStop;
                break;
            }
            ForcedMovementStepConstraintResolution.Decision constraintDecision =
                    ForcedMovementStepConstraintResolution.evaluate(constraints, candidate, movingSize);
            if (!constraintDecision.allowed()) {
                stop = new Stop(StopReason.STEP_CONSTRAINT, candidate, null, null);
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
                List.copyOf(traversed),
                stop
        );
    }

    private static Stop stopFor(BattleRuntimeState state, String movingId, GridCoord anchor) {
        MovementGrid grid = state.grid();
        String movingSize = state.geometry(movingId).sizeLabel();
        Set<GridCoord> candidateFootprint = Targeting.footprintTiles(anchor, movingSize);

        for (GridCoord tile : candidateFootprint) {
            if (!grid.inBounds(tile)) {
                return new Stop(StopReason.OUT_OF_BOUNDS, anchor, tile, null);
            }
            if (grid.isBlocker(tile)) {
                return new Stop(StopReason.BLOCKER, anchor, tile, null);
            }
        }

        for (String otherId : state.combatantIds()) {
            if (otherId.equals(movingId)) continue;
            RuntimeCombatantState other = state.requireCombatant(otherId);
            if (other.hp() <= 0) continue;
            Set<GridCoord> otherFootprint = Targeting.footprintTiles(
                    other.position(), state.geometry(otherId).sizeLabel()
            );
            for (GridCoord tile : candidateFootprint) {
                if (otherFootprint.contains(tile)) {
                    return new Stop(StopReason.OCCUPIED, anchor, tile, otherId);
                }
            }
        }
        return Stop.none();
    }
}
