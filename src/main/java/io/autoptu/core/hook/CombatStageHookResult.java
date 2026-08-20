package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.List;

/** Ordered semantic events emitted by one combat-stage reaction hook. */
public record CombatStageHookResult(List<BattleEvent> events) {
    public CombatStageHookResult {
        if (events == null || events.isEmpty()) {
            events = List.of();
        } else {
            ArrayList<BattleEvent> copy = new ArrayList<>(events.size());
            for (BattleEvent event : events) {
                if (event == null) throw new IllegalArgumentException("combat-stage hook event cannot be null");
                copy.add(event);
            }
            events = List.copyOf(copy);
        }
    }

    public static CombatStageHookResult empty() {
        return new CombatStageHookResult(List.of());
    }

    public static CombatStageHookResult events(List<BattleEvent> events) {
        return new CombatStageHookResult(events);
    }
}
