package io.autoptu.core.rules;

import io.autoptu.core.model.AttackModifier;
import io.autoptu.core.model.DamageCheck;
import io.autoptu.core.model.DamageResult;
import io.autoptu.core.random.PythonRandom;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageResolutionTest {
    @Test
    void normalDamageUsesDbDiceAttackDefenseThenType() {
        DamageResult result = DamageResolution.resolve(
                new PythonRandom(1234),
                new DamageCheck(6, 12, 7, false, false, 1.5, List.of())
        );
        assertEquals(result.preModifierDamage(), result.damageRoll() + 5);
        assertEquals((int) Math.floor(result.preTypeDamage() * 1.5), result.damage());
    }

    @Test
    void defenseCannotPushOrdinaryPreModifierDamageBelowOne() {
        DamageResult result = DamageResolution.resolve(
                new PythonRandom(7),
                new DamageCheck(2, 1, 999, false, false, 1.0, List.of())
        );
        assertEquals(1, result.preModifierDamage());
        assertEquals(1, result.damage());
    }

    @Test
    void criticalAddsOnlyExtraDiceNotFlatModifier() {
        DamageResult normal = DamageResolution.resolve(
                new PythonRandom(99),
                new DamageCheck(5, 0, 0, false, false, 1.0, List.of())
        );
        DamageResult critical = DamageResolution.resolve(
                new PythonRandom(99),
                new DamageCheck(5, 0, 0, true, false, 1.0, List.of())
        );
        assertEquals(normal.baseRoll(), critical.baseRoll());
        assertEquals(critical.baseRoll() + critical.criticalExtraRoll(), critical.damageRoll());
    }

    @Test
    void sniperAddsTheSameCriticalExtraRollAgain() {
        DamageResult result = DamageResolution.resolve(
                new PythonRandom(5),
                new DamageCheck(8, 0, 0, true, true, 1.0, List.of())
        );
        assertEquals(
                result.baseRoll() + 2 * result.criticalExtraRoll(),
                result.damageRoll()
        );
    }

    @Test
    void flatModifiersRunBeforeOrderedScalars() {
        DamageResult result = DamageResolution.resolve(
                new PythonRandom(42),
                new DamageCheck(
                        4, 10, 5, false, false, 1.0,
                        List.of(
                                AttackModifier.scalar("half", 0.5),
                                AttackModifier.flat("flat", 3),
                                AttackModifier.scalar("boost", 1.5)
                        )
                )
        );
        int expected = (int) Math.floor(Math.floor((result.preModifierDamage() + 3) * 0.5) * 1.5);
        assertEquals(expected, result.preTypeDamage());
    }

    @Test
    void immunityMultiplierProducesZeroAfterPreservingPreTypeDamage() {
        DamageResult result = DamageResolution.resolve(
                new PythonRandom(12),
                new DamageCheck(10, 20, 1, false, false, 0.0, List.of())
        );
        assertEquals(0, result.damage());
    }
}
