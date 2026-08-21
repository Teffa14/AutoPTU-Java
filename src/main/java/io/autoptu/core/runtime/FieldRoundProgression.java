package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.FieldEffectEndedEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Pure ROUND_START progression for duration-bearing terrain, zones, and rooms.
 *
 * Python advances these families in the order terrain -> zones -> rooms before
 * delayed-hit maturity. This resolver preserves that order and emits cleanup
 * requests rather than reaching into combatants directly.
 */
public final class FieldRoundProgression {
    private static final Set<String> WONDER_ROOM_STATUSES = Set.of("wondered", "wonder room");

    private FieldRoundProgression() {}

    public static FieldRoundProgressionResult advance(
            int round,
            FieldEffectEntry terrain,
            Collection<FieldEffectEntry> zones,
            Collection<FieldEffectEntry> rooms
    ) {
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
        validateKind(terrain, FieldEffectKind.TERRAIN);
        List<FieldEffectEntry> zoneEntries = copyAndValidate(zones, FieldEffectKind.ZONE);
        List<FieldEffectEntry> roomEntries = copyAndValidate(rooms, FieldEffectKind.ROOM);

        ArrayList<BattleEvent> events = new ArrayList<>();
        ArrayList<FieldStatusCleanupRequest> cleanups = new ArrayList<>();

        Optional<FieldEffectEntry> nextTerrain = Optional.empty();
        if (terrain != null) {
            FieldEffectEntry next = advanceOne(terrain, round, events, cleanups);
            nextTerrain = Optional.ofNullable(next);
        }

        List<FieldEffectEntry> nextZones = advanceMany(zoneEntries, round, events, cleanups);
        List<FieldEffectEntry> nextRooms = advanceMany(roomEntries, round, events, cleanups);
        return new FieldRoundProgressionResult(nextTerrain, nextZones, nextRooms, events, cleanups);
    }

    private static List<FieldEffectEntry> advanceMany(
            List<FieldEffectEntry> entries,
            int round,
            List<BattleEvent> events,
            List<FieldStatusCleanupRequest> cleanups
    ) {
        ArrayList<FieldEffectEntry> remaining = new ArrayList<>();
        for (FieldEffectEntry entry : entries) {
            FieldEffectEntry next = advanceOne(entry, round, events, cleanups);
            if (next != null) remaining.add(next);
        }
        return List.copyOf(remaining);
    }

    private static FieldEffectEntry advanceOne(
            FieldEffectEntry entry,
            int round,
            List<BattleEvent> events,
            List<FieldStatusCleanupRequest> cleanups
    ) {
        Integer ticks = entry.remaining();
        if (ticks == null) return entry;
        int next = Math.max(0, ticks - 1);
        if (next > 0) return entry.withRemaining(next);

        events.add(new FieldEffectEndedEvent(entry.kind(), entry.name(), round));
        if (entry.kind() == FieldEffectKind.ROOM
                && entry.name().strip().toLowerCase(Locale.ROOT).equals("wonder room")) {
            cleanups.add(new FieldStatusCleanupRequest(WONDER_ROOM_STATUSES));
        }
        return null;
    }

    private static List<FieldEffectEntry> copyAndValidate(
            Collection<FieldEffectEntry> entries,
            FieldEffectKind expectedKind
    ) {
        ArrayList<FieldEffectEntry> copied = new ArrayList<>();
        if (entries == null) return copied;
        for (FieldEffectEntry entry : entries) {
            if (entry == null) continue;
            validateKind(entry, expectedKind);
            copied.add(entry);
        }
        return copied;
    }

    private static void validateKind(FieldEffectEntry entry, FieldEffectKind expectedKind) {
        if (entry != null && entry.kind() != expectedKind) {
            throw new IllegalArgumentException(
                    "expected " + expectedKind.wireName() + " entry but got " + entry.kind().wireName()
            );
        }
    }
}
