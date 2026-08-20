package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.List;

/** Ordered additive final-damage effects plus semantic playback events. */
public record PostDamageHookResult(int flatDamageBonus, List<BattleEvent> events) {
    public PostDamageHookResult {
        if (flatDamageBonus < 0) throw new IllegalArgumentException("flatDamageBonus cannot be negative");
        if (events == null || events.isEmpty()) {
            events = List.of();
        } else {
            ArrayList<BattleEvent> copy = new ArrayList<>(events.size());
            for (BattleEvent event : events) {
                if (event == null) throw new IllegalArgumentException("post-damage event cannot be null");
                copy.add(event);
            }
            events = List.copyOf(copy);
        }
    }

    public static PostDamageHookResult empty() {
        return new PostDamageHookResult(0, List.of());
    }
}
