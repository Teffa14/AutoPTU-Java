package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.AttackModifier;

import java.util.ArrayList;
import java.util.List;

/** Ordered authoritative output from one or more damage hooks. */
public record DamageModifierHookResult(
        List<AttackModifier> modifiers,
        List<BattleEvent> events
) {
    public DamageModifierHookResult {
        modifiers = copyModifiers(modifiers);
        events = copyEvents(events);
    }

    public static DamageModifierHookResult empty() {
        return new DamageModifierHookResult(List.of(), List.of());
    }

    public static DamageModifierHookResult modifiersOnly(List<AttackModifier> modifiers) {
        return new DamageModifierHookResult(modifiers, List.of());
    }

    public static DamageModifierHookResult of(List<AttackModifier> modifiers, List<BattleEvent> events) {
        return new DamageModifierHookResult(modifiers, events);
    }

    private static List<AttackModifier> copyModifiers(List<AttackModifier> values) {
        if (values == null || values.isEmpty()) return List.of();
        ArrayList<AttackModifier> copied = new ArrayList<>(values.size());
        for (AttackModifier value : values) {
            if (value == null) throw new IllegalArgumentException("damage hook modifier cannot be null");
            copied.add(value);
        }
        return List.copyOf(copied);
    }

    private static List<BattleEvent> copyEvents(List<BattleEvent> values) {
        if (values == null || values.isEmpty()) return List.of();
        ArrayList<BattleEvent> copied = new ArrayList<>(values.size());
        for (BattleEvent value : values) {
            if (value == null) throw new IllegalArgumentException("damage hook event cannot be null");
            copied.add(value);
        }
        return List.copyOf(copied);
    }
}
