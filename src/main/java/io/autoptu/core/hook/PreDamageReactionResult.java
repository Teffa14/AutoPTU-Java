package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;

import java.util.List;

/** Mutable attack outcome projected immutably between ordered pre-damage hooks. */
public record PreDamageReactionResult(
        boolean hit,
        int damage,
        double typeMultiplier,
        List<BattleEvent> events
) {
    public PreDamageReactionResult {
        if (damage < 0) throw new IllegalArgumentException("damage cannot be negative");
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static PreDamageReactionResult of(boolean hit, int damage, double typeMultiplier) {
        return new PreDamageReactionResult(hit, damage, typeMultiplier, List.of());
    }

    /** Python Telepathy/Perception cancellation semantics. */
    public PreDamageReactionResult cancelHit(List<BattleEvent> addedEvents) {
        java.util.ArrayList<BattleEvent> merged = new java.util.ArrayList<>(events);
        if (addedEvents != null) merged.addAll(addedEvents);
        return new PreDamageReactionResult(false, 0, 0.0, merged);
    }
}
