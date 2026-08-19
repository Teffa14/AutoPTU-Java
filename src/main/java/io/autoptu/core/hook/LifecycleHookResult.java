package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.List;

/** Ordered semantic output produced by one lifecycle hook. */
public record LifecycleHookResult(
        List<BattleEvent> events,
        PendingStatusSkipRequest pendingStatusSkip
) {
    public LifecycleHookResult {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public LifecycleHookResult(List<BattleEvent> events) {
        this(events, null);
    }

    public static LifecycleHookResult empty() {
        return new LifecycleHookResult(List.of(), null);
    }

    public static LifecycleHookResult events(List<BattleEvent> events) {
        return new LifecycleHookResult(events, null);
    }

    public static LifecycleHookResult pendingStatusSkip(PendingStatusSkipRequest request) {
        return new LifecycleHookResult(List.of(), request);
    }

    public static LifecycleHookResult eventsAndPendingStatusSkip(
            List<BattleEvent> events,
            PendingStatusSkipRequest request
    ) {
        return new LifecycleHookResult(events, request);
    }
}
