package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Server-owned terrain/zone/room state advanced during ROUND_START.
 *
 * Minecraft/Cobblemon may project these effects visually, but duration progression and
 * expiry are committed here by the battle lifecycle.
 */
public final class BattleFieldState {
    private FieldEffectEntry terrain;
    private List<FieldEffectEntry> zones = List.of();
    private List<FieldEffectEntry> rooms = List.of();

    public Optional<FieldEffectEntry> terrain() {
        return Optional.ofNullable(terrain);
    }

    public List<FieldEffectEntry> zones() {
        return zones;
    }

    public List<FieldEffectEntry> rooms() {
        return rooms;
    }

    void replaceFromRuntime(
            FieldEffectEntry terrain,
            Collection<FieldEffectEntry> zones,
            Collection<FieldEffectEntry> rooms
    ) {
        validateKind(terrain, FieldEffectKind.TERRAIN);
        this.terrain = terrain;
        this.zones = copyAndValidate(zones, FieldEffectKind.ZONE);
        this.rooms = copyAndValidate(rooms, FieldEffectKind.ROOM);
    }

    FieldRoundProgressionResult advanceRoundFromLifecycle(int round) {
        FieldRoundProgressionResult result = FieldRoundProgression.advance(round, terrain, zones, rooms);
        terrain = result.terrain().orElse(null);
        zones = result.zones();
        rooms = result.rooms();
        return result;
    }

    private static List<FieldEffectEntry> copyAndValidate(
            Collection<FieldEffectEntry> entries,
            FieldEffectKind expectedKind
    ) {
        ArrayList<FieldEffectEntry> copied = new ArrayList<>();
        if (entries == null) return List.of();
        for (FieldEffectEntry entry : entries) {
            if (entry == null) continue;
            validateKind(entry, expectedKind);
            copied.add(entry);
        }
        return List.copyOf(copied);
    }

    private static void validateKind(FieldEffectEntry entry, FieldEffectKind expectedKind) {
        if (entry != null && entry.kind() != expectedKind) {
            throw new IllegalArgumentException(
                    "expected " + expectedKind.wireName() + " entry but got " + entry.kind().wireName()
            );
        }
    }
}
