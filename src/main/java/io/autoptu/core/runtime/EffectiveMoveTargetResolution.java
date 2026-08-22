package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.List;

/** Immutable result of authoritative move-area target selection. */
public record EffectiveMoveTargetResolution(
        GridCoord anchor,
        List<GridCoord> affectedTiles,
        List<String> targetIds
) {
    public EffectiveMoveTargetResolution {
        if (anchor == null) {
            throw new IllegalArgumentException("anchor is required");
        }
        affectedTiles = List.copyOf(affectedTiles == null ? List.of() : affectedTiles);
        targetIds = List.copyOf(targetIds == null ? List.of() : targetIds);
    }
}
