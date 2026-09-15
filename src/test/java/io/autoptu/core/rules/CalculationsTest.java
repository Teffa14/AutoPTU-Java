package io.autoptu.core.rules;

import io.autoptu.core.model.AttackModifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculationsTest {
    @Test
    void clampsCombatStagesToPtuBounds() {
        assertEquals(-6, Calculations.clampStage(-99));
        assertEquals(-4, Calculations.clampStage(-4));
        assertEquals(0, Calculations.clampStage(0));
        assertEquals(5, Calculations.clampStage(5));
        assertEquals(6, Calculations.clampStage(99));
    }

    @Test
    void stageMultipliersMatchPtu105AndKairosRulebook() {
        assertEquals(0.4, Calculations.stageMultiplier(-6), 1e-12);
        assertEquals(0.8, Calculations.stageMultiplier(-2), 1e-12);
        assertEquals(0.9, Calculations.stageMultiplier(-1), 1e-12);
        assertEquals(1.0, Calculations.stageMultiplier(0), 1e-12);
        assertEquals(1.2, Calculations.stageMultiplier(1), 1e-12);
        assertEquals(1.4, Calculations.stageMultiplier(2), 1e-12);
        assertEquals(2.2, Calculations.stageMultiplier(6), 1e-12);
    }

    @Test
    void accuracyStagesAreFlatAndClamped() {
        assertEquals(-6, Calculations.accuracyStageValue(-20));
        assertEquals(3, Calculations.accuracyStageValue(3));
        assertEquals(6, Calculations.accuracyStageValue(20));
    }

    @Test
    void weatherDamageBaseModifiersMatchPythonTable() {
        assertEquals(1, Calculations.weatherDbModifier("Rain", "Water"));
        assertEquals(1, Calculations.weatherDbModifier(" storm ", "ELECTRIC"));
        assertEquals(-1, Calculations.weatherDbModifier("Downpour", "Fire"));
        assertEquals(1, Calculations.weatherDbModifier("Harsh Sunlight", "Fire"));
        assertEquals(-1, Calculations.weatherDbModifier("Sunny", "Water"));
        assertEquals(1, Calculations.weatherDbModifier("Hail", "Ice"));
        assertEquals(1, Calculations.weatherDbModifier("Sandstorm", "Rock"));
        assertEquals(0, Calculations.weatherDbModifier("Rain", "Grass"));
        assertEquals(0, Calculations.weatherDbModifier("None", "Fire"));
    }

    @Test
    void critProbabilityIsBoundedByHitChance() {
        assertEquals(0.05, Calculations.critProbability(20, 1.0), 1e-12);
        assertEquals(0.15, Calculations.critProbability(18, 1.0), 1e-12);
        assertEquals(0.10, Calculations.critProbability(18, 0.10), 1e-12);
        assertEquals(0.05, Calculations.critProbability(0, 1.0), 1e-12);
    }

    @Test
    void burnHalvesOnlyPhysicalDamageAndFloors() {
        assertEquals(50, Calculations.applyStatusModifiers(101, "Physical", true));
        assertEquals(101, Calculations.applyStatusModifiers(101, "Special", true));
        assertEquals(101, Calculations.applyStatusModifiers(101, "Physical", false));
    }

    @Test
    void damageModifiersApplyAllFlatThenScalarsWithFloorPerScalar() {
        List<AttackModifier> modifiers = List.of(
                AttackModifier.scalar("half", 0.5),
                AttackModifier.flat("bonus-a", 5),
                AttackModifier.scalar("third", 1.0 / 3.0),
                AttackModifier.flat("bonus-b", 2)
        );
        assertEquals(17, Calculations.applyContextDamageModifiers(100, modifiers));
    }

    @Test
    void rangeKindUsesRangeKindFirstAndOnlyDistinguishesMelee() {
        assertEquals("melee", Calculations.normalizedRangeKind("Melee, 1 Target", "Ranged"));
        assertEquals("ranged", Calculations.normalizedRangeKind("Cone", "Melee"));
        assertEquals("melee", Calculations.normalizedRangeKind("", "Light Melee"));
        assertEquals("ranged", Calculations.normalizedRangeKind(null, null));
    }

    @Test
    void finalTypeMultiplierUsesFloor() {
        assertEquals(50, Calculations.applyTypeMultiplierFloor(101, 0.5));
        assertEquals(151, Calculations.applyTypeMultiplierFloor(101, 1.5));
        assertEquals(202, Calculations.applyTypeMultiplierFloor(101, 2.0));
    }
}
