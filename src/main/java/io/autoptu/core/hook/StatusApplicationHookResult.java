package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.List;

/** Result from one status-application hook. Blocked applications stop later hooks. */
public record StatusApplicationHookResult(boolean blocked, List<BattleEvent> events) {
    public StatusApplicationHookResult {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static StatusApplicationHookResult allow() {
        return new StatusApplicationHookResult(false, List.of());
    }

    public static StatusApplicationHookResult allow(List<BattleEvent> events) {
        return new StatusApplicationHookResult(false, events);
    }

    public static StatusApplicationHookResult block(List<BattleEvent> events) {
        return new StatusApplicationHookResult(true, events);
    }
}
