package io.autoptu.core.model;

/**
 * BattleState-independent inputs for the invariant d20 accuracy resolver.
 *
 * Ability/status/item logic should resolve its bonuses before constructing this
 * value. The core still owns PTU's clamping, natural-roll, Blur, melee No Guard,
 * critical threshold, and optional Probability Control reroll semantics.
 */
public record AccuracyCheck(
        Integer moveAc,
        int evasion,
        int accuracyStage,
        int roll,
        Integer reroll,
        int critRange,
        boolean meleeNoGuard,
        boolean blurApplies
) {
    public AccuracyCheck {
        if (roll < 1 || roll > 20) {
            throw new IllegalArgumentException("roll must be between 1 and 20");
        }
        if (reroll != null && (reroll < 1 || reroll > 20)) {
            throw new IllegalArgumentException("reroll must be between 1 and 20");
        }
    }
}
