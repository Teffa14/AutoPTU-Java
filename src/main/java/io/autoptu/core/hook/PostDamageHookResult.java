package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.DamageResult;

import java.util.List;

/** Ordered post-damage output: updated final damage plus semantic playback events. */
public record PostDamageHookResult(
        DamageResult damage,
        List<BattleEvent> events
) {
    public PostDamageHookResult {
        if (damage == null) throw new IllegalArgumentException("damage is required");
        events = List.copyOf(events == null ? List.of() : events);
    }

    public static PostDamageHookResult unchanged(DamageResult damage) {
        return new PostDamageHookResult(damage, List.of());
    }

    public static PostDamageHookResult of(DamageResult damage, List<? extends BattleEvent> events) {
        return new PostDamageHookResult(damage, events == null ? List.of() : List.copyOf(events));
    }
}
