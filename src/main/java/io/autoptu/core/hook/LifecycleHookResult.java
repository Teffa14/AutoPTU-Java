package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.List;

/** Ordered semantic output produced by one lifecycle hook. */
public record LifecycleHookResult(List<BattleEvent> events) {
    public LifecycleHookResult {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static LifecycleHookResult empty() {
        return new LifecycleHookResult(List.of());
    }

    public static LifecycleHookResult events(List<BattleEvent> events) {
        return new LifecycleHookResult(events);
    }
}
