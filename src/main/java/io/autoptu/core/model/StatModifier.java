package io.autoptu.core.model;

/** Pre-stage additive/scalar and post-stage bonus already resolved from hooks/items/features. */
public record StatModifier(
        int additive,
        double scalar,
        int postStageBonus
) {
    public StatModifier {
        if (scalar <= 0.0) {
            throw new IllegalArgumentException("scalar must be positive");
        }
    }

    public static StatModifier identity() {
        return new StatModifier(0, 1.0, 0);
    }
}
