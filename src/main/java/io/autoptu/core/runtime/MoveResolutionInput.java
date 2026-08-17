package io.autoptu.core.runtime;

import io.autoptu.core.model.AccuracyCheck;
import io.autoptu.core.model.AttackModifier;
import io.autoptu.core.model.DamageCheck;

import java.util.List;

/**
 * Stateful-runtime inputs for one direct combatant move after rule hooks have
 * selected effective accuracy, damage-base, stats, and modifiers.
 *
 * Random rolls and critical state are deliberately absent. BattleRuntime owns
 * those values so an AI, Minecraft client, or adapter cannot inject outcomes.
 */
public record MoveResolutionInput(
        Integer moveAc,
        int evasion,
        int accuracyStage,
        int critRange,
        boolean meleeNoGuard,
        boolean blurApplies,
        boolean rerollOnMiss,
        int effectiveDb,
        int attackValue,
        int defenseValue,
        boolean sniper,
        double typeMultiplier,
        List<AttackModifier> modifiers
) {
    public MoveResolutionInput {
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        if (typeMultiplier < 0.0) {
            throw new IllegalArgumentException("typeMultiplier cannot be negative");
        }
    }

    AccuracyCheck accuracyCheck(int roll, Integer reroll) {
        return new AccuracyCheck(
                moveAc,
                evasion,
                accuracyStage,
                roll,
                reroll,
                critRange,
                meleeNoGuard,
                blurApplies
        );
    }

    DamageCheck damageCheck(boolean critical) {
        return new DamageCheck(
                effectiveDb,
                attackValue,
                defenseValue,
                critical,
                sniper,
                typeMultiplier,
                modifiers
        );
    }
}
