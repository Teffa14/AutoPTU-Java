package io.autoptu.core.rules;

import io.autoptu.core.model.GridCoord;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Shared spatial contract for reaction movement that escapes an incoming area attack.
 *
 * Python Perception/Telepathy first derive legal Shift destinations, remove tiles that
 * remain inside the threatened area, optionally restrict displacement distance, then
 * choose the farthest surviving tile. Ties preserve the first candidate encountered,
 * matching Python max(..., key=...) over the filtered candidate sequence.
 */
public final class ReactionEscapeMovementResolution {
    private ReactionEscapeMovementResolution() {
    }

    public static Optional<GridCoord> chooseDestination(
            GridCoord origin,
            Collection<GridCoord> reachableTiles,
            Collection<GridCoord> threatenedTiles,
            Integer maxDistance
    ) {
        if (origin == null) {
            throw new IllegalArgumentException("origin is required");
        }
        Collection<GridCoord> reachable = reachableTiles == null ? java.util.List.of() : reachableTiles;
        Set<GridCoord> threatened = threatenedTiles == null
                ? Set.of()
                : new LinkedHashSet<>(threatenedTiles);
        int limit = maxDistance == null ? Integer.MAX_VALUE : Math.max(0, maxDistance);

        GridCoord best = null;
        int bestDistance = Integer.MIN_VALUE;
        for (GridCoord candidate : reachable) {
            if (candidate == null || threatened.contains(candidate)) {
                continue;
            }
            int distance = Targeting.chebyshevDistance(origin, candidate);
            if (distance > limit) {
                continue;
            }
            if (best == null || distance > bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    public static Optional<GridCoord> chooseDestination(
            GridCoord origin,
            Collection<GridCoord> reachableTiles,
            Collection<GridCoord> threatenedTiles
    ) {
        return chooseDestination(origin, reachableTiles, threatenedTiles, null);
    }
}
