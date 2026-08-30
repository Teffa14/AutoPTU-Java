package io.autoptu.core.rules;

import io.autoptu.core.model.GridCoord;

import java.util.List;

/**
 * Canonical integer grid-line geometry shared by targeting, line of sight and reactions.
 *
 * <p>This intentionally delegates to the same Bresenham primitive already used by
 * {@link Targeting#lineOfSightClear}. Its endpoint and tie-breaking semantics are frozen
 * against the pinned Python {@code BattleState._line_cells} oracle.</p>
 */
public final class GridLineResolution {
    private GridLineResolution() {}

    public static List<GridCoord> cells(GridCoord origin, GridCoord target) {
        if (origin == null) throw new IllegalArgumentException("origin is required");
        if (target == null) throw new IllegalArgumentException("target is required");
        return List.copyOf(Targeting.bresenhamCells(origin, target));
    }
}
