package io.autoptu.core.model;

import java.util.List;

/** Inputs to the invariant PTU damage-roll pipeline after rule hooks select the effective values. */
public record DamageCheck(
        int effectiveDb,
        int attackValue,
        int defenseValue,
        boolean critical,
        boolean sniper,
        double typeMultiplier,
        List<AttackModifier> modifiers
) {
    public DamageCheck {
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        if (typeMultiplier < 0.0) {
            throw new IllegalArgumentException("typeMultiplier cannot be negative");
        }
    }
}
