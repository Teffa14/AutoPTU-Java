package io.autoptu.core.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Terrain subset required by PTU movement legality.
 *
 * Presentation-specific Minecraft block data should be translated into this model
 * before the rules engine sees it.
 */
public final class MovementGrid {
    private final int width;
    private final int height;
    private final Set<GridCoord> blockers;
    private final Map<GridCoord, String> tileTypes;

    public MovementGrid(
            int width,
            int height,
            Set<GridCoord> blockers,
            Map<GridCoord, String> tileTypes
    ) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Grid dimensions cannot be negative.");
        }
        this.width = width;
        this.height = height;
        this.blockers = new LinkedHashSet<>(blockers == null ? Set.of() : blockers);
        this.tileTypes = new LinkedHashMap<>(tileTypes == null ? Map.of() : tileTypes);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean inBounds(GridCoord coord) {
        return coord.x() >= 0 && coord.y() >= 0 && coord.x() < width && coord.y() < height;
    }

    public boolean isBlocker(GridCoord coord) {
        return blockers.contains(coord);
    }

    public String tileType(GridCoord coord) {
        return tileTypes.getOrDefault(coord, "");
    }

    public Set<GridCoord> blockers() {
        return Set.copyOf(blockers);
    }

    public Map<GridCoord, String> tileTypes() {
        return Map.copyOf(tileTypes);
    }
}
