package io.autoptu.core.rules;

import io.autoptu.core.model.DamageCheck;
import io.autoptu.core.model.DamageDice;
import io.autoptu.core.model.DamageResult;
import io.autoptu.core.random.PythonRandom;

/**
 * Invariant PTU damage pipeline extracted from calculations.resolve_move_action.
 *
 * Move-specific stat selection, immunities, abilities, and effective-DB hooks are
 * resolved before entering this class. This class owns the dice consumption and
 * arithmetic ordering that must stay identical to Python.
 */
public final class DamageResolution {
    private DamageResolution() {
    }

    public static DamageResult resolve(PythonRandom rng, DamageCheck check) {
        if (rng == null) {
            throw new IllegalArgumentException("rng is required");
        }
        if (check == null) {
            throw new IllegalArgumentException("check is required");
        }

        DamageDice dice = PtuTables.dbToDice(check.effectiveDb());
        int baseRoll = rollDice(rng, dice.count(), dice.sides()) + dice.flat();
        int criticalExtra = 0;
        int damageRoll = baseRoll;
        if (check.critical()) {
            criticalExtra = rollDice(rng, dice.count(), dice.sides());
            damageRoll += criticalExtra;
            if (check.sniper()) {
                damageRoll += criticalExtra;
            }
        }

        int preModifier = Math.max(0, damageRoll + check.attackValue() - check.defenseValue());
        int preType = Calculations.applyContextDamageModifiers(preModifier, check.modifiers());
        int damage = Calculations.applyTypeMultiplierFloor(preType, check.typeMultiplier());

        return new DamageResult(
                dice,
                baseRoll,
                criticalExtra,
                damageRoll,
                preModifier,
                preType,
                damage
        );
    }

    private static int rollDice(PythonRandom rng, int count, int sides) {
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += rng.randIntInclusive(1, sides);
        }
        return total;
    }
}
