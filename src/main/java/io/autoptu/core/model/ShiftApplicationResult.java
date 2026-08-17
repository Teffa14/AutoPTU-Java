package io.autoptu.core.model;

import io.autoptu.core.event.ShiftResolvedEvent;

/** Pure transition output used by headless simulation and Minecraft playback. */
public record ShiftApplicationResult(
        GridCoord position,
        ShiftResolvedEvent event
) {
    public ShiftApplicationResult {
        if (position == null) throw new IllegalArgumentException("position is required");
        if (event == null) throw new IllegalArgumentException("event is required");
        if (!position.equals(event.destination())) {
            throw new IllegalArgumentException("position must equal event destination");
        }
    }
}
