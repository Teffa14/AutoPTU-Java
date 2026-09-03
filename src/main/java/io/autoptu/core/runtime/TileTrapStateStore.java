package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-owned tile trap state matching Python grid.tiles[coord].traps plus trap_sources.
 *
 * <p>The store preserves tile and trap insertion order for deterministic parity. It owns
 * mutable battle state; Minecraft/Cobblemon adapters may materialize initial entries but
 * must not re-evaluate trap legality or consumption.</p>
 */
final class TileTrapStateStore {
    private final LinkedHashMap<GridCoord, LinkedHashMap<String, TileEntryTrapResolution.TrapLayer>> byTile =
            new LinkedHashMap<>();

    List<TileEntryTrapResolution.TrapLayer> entries(GridCoord coordinate) {
        requireCoordinate(coordinate);
        LinkedHashMap<String, TileEntryTrapResolution.TrapLayer> traps = byTile.get(coordinate);
        if (traps == null) return List.of();
        return List.copyOf(traps.values());
    }

    Map<GridCoord, List<TileEntryTrapResolution.TrapLayer>> snapshot() {
        LinkedHashMap<GridCoord, List<TileEntryTrapResolution.TrapLayer>> copy = new LinkedHashMap<>();
        for (Map.Entry<GridCoord, LinkedHashMap<String, TileEntryTrapResolution.TrapLayer>> entry : byTile.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue().values()));
        }
        return Map.copyOf(copy);
    }

    void replace(GridCoord coordinate, Collection<TileEntryTrapResolution.TrapLayer> traps) {
        requireCoordinate(coordinate);
        LinkedHashMap<String, TileEntryTrapResolution.TrapLayer> normalized = new LinkedHashMap<>();
        for (TileEntryTrapResolution.TrapLayer trap : traps == null
                ? List.<TileEntryTrapResolution.TrapLayer>of()
                : traps) {
            if (trap == null) continue;
            normalized.put(trap.trapKey(), trap);
        }
        if (normalized.isEmpty()) {
            byTile.remove(coordinate);
        } else {
            byTile.put(coordinate, normalized);
        }
    }

    void put(GridCoord coordinate, TileEntryTrapResolution.TrapLayer trap) {
        requireCoordinate(coordinate);
        if (trap == null) throw new IllegalArgumentException("trap is required");
        byTile.computeIfAbsent(coordinate, ignored -> new LinkedHashMap<>())
                .put(trap.trapKey(), trap);
    }

    /** Python _consume_trap removes the complete trap key and its source metadata. */
    boolean consume(GridCoord coordinate, String trapKey) {
        requireCoordinate(coordinate);
        String canonicalKey = canonicalKey(trapKey);
        LinkedHashMap<String, TileEntryTrapResolution.TrapLayer> traps = byTile.get(coordinate);
        if (traps == null) return false;
        boolean removed = traps.remove(canonicalKey) != null;
        if (traps.isEmpty()) byTile.remove(coordinate);
        return removed;
    }

    private static void requireCoordinate(GridCoord coordinate) {
        if (coordinate == null) throw new IllegalArgumentException("coordinate is required");
    }

    private static String canonicalKey(String trapKey) {
        if (trapKey == null || trapKey.isBlank()) throw new IllegalArgumentException("trapKey is required");
        return trapKey.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
