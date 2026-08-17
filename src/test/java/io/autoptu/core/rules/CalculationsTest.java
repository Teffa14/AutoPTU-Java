package io.autoptu.core.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculationsTest {
    @Test
    void clampsCombatStagesExactlyLikePython() {
        assertEquals(-6, Calculations.clampStage(-99));
        assertEquals(-4, Calculations.clampStage(-4));
        assertEquals(0, Calculations.clampStage(0));
        assertEquals(5, Calculations.clampStage(5));
        assertEquals(6, Calculations.clampStage(99));
    }

    @Test
    void stageMultipliersMatchPythonFormula() {
        assertEquals(0.25, Calculations.stageMultiplier(-6), 1e-12);
        assertEquals(0.5, Calculations.stageMultiplier(-2), 1e-12);
        assertEquals(2.0 / 3.0, Calculations.stageMultiplier(-1), 1e-12);
        assertEquals(1.0, Calculations.stageMultiplier(0), 1e-12);
        assertEquals(1.5, Calculations.stageMultiplier(1), 1e-12);
        assertEquals(2.0, Calculations.stageMultiplier(2), 1e-12);
        assertEquals(4.0, Calculations.stageMultiplier(6), 1e-12);
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
}
