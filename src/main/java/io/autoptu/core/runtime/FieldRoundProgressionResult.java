package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

import java.util.List;
import java.util.Optional;

/** Immutable result of one Python-compatible terrain/zone/room ROUND_START progression step. */
public record FieldRoundProgressionResult(
        Optional<FieldEffectEntry> terrain,
        List<FieldEffectEntry> zones,
        List<FieldEffectEntry> rooms,
        List<BattleEvent> events,
        List<FieldStatusCleanupRequest> statusCleanups
) {
    public FieldRoundProgressionResult {
        terrain = terrain == null ? Optional.empty() : terrain;
        zones = List.copyOf(zones == null ? List.of() : zones);
        rooms = List.copyOf(rooms == null ? List.of() : rooms);
        events = List.copyOf(events == null ? List.of() : events);
        statusCleanups = List.copyOf(statusCleanups == null ? List.of() : statusCleanups);
    }
}
