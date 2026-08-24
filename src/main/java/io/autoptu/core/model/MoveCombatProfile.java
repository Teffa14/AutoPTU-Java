package io.autoptu.core.model;

import java.util.Locale;

/**
 * Intrinsic combat metadata for a move.
 *
 * These values come from authoritative AutoPTU move data. Minecraft adapters and
 * AI controllers may select a move id, but they must not supply or override its
 * AC, damage base, critical range, damage category, or elemental type.
 */
public record MoveCombatProfile(
        Integer ac,
        int damageBase,
        int critRange,
        String damageCategory,
        String moveType
) {
    public MoveCombatProfile {
        if (damageBase < 0) {
            throw new IllegalArgumentException("damageBase cannot be negative");
        }
        if (critRange < 1 || critRange > 20) {
            throw new IllegalArgumentException("critRange must be between 1 and 20");
        }
        if (damageCategory == null || damageCategory.isBlank()) {
            throw new IllegalArgumentException("damageCategory is required");
        }
        damageCategory = damageCategory.strip().toLowerCase(Locale.ROOT);
        if (!damageCategory.equals("physical")
                && !damageCategory.equals("special")
                && !damageCategory.equals("status")) {
            throw new IllegalArgumentException("damageCategory must be physical, special, or status");
        }
        if (moveType != null) {
            moveType = moveType.strip();
            if (moveType.isEmpty()) {
                moveType = null;
            }
        }
    }

    /** Transitional constructor for already-ported callers that do not load move type yet. */
    public MoveCombatProfile(Integer ac, int damageBase, int critRange, String damageCategory) {
        this(ac, damageBase, critRange, damageCategory, null);
    }
}
