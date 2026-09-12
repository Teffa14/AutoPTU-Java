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
 * arithmetic ordering. Rule-bearing arithmetic follows PTU; documented Python
 * oracle divergences must not override an explicit rulebook invariant.
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

        // PTU 1.05/Kairos: an ordinary damaging attack does at least 1 damage
        // after the relevant Defense has been subtracted. Type immunity remains
        // a later, separate rule and may still reduce final damage to 0.
        int preModifier = Math.max(1, damageRoll + check.attackValue() - check.defenseValue());
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
