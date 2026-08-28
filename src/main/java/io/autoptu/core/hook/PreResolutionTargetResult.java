package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.List;

/** Effective target plus semantic events produced before the ordinary move pipeline starts. */
public record PreResolutionTargetResult(String targetId, List<BattleEvent> events) {
    public PreResolutionTargetResult {
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");
        targetId = targetId.strip();
        events = events == null ? List.of() : List.copyOf(events);
        if (events.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("events cannot contain null");
        }
    }

    public static PreResolutionTargetResult initial(String targetId) {
        return new PreResolutionTargetResult(targetId, List.of());
    }

    public PreResolutionTargetResult replaceTarget(String replacementTargetId, List<? extends BattleEvent> addedEvents) {
        ArrayList<BattleEvent> combined = new ArrayList<>(events);
        if (addedEvents != null) combined.addAll(addedEvents);
        return new PreResolutionTargetResult(replacementTargetId, combined);
    }
}
