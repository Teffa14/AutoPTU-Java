package io.autoptu.core.rules;

import io.autoptu.core.model.DamageDice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PtuTablesTest {
    @Test
    void damageBaseTableMatchesKnownCoreEntriesAndExtension() {
        assertEquals(new DamageDice(1, 6, 3), PtuTables.dbToDice(2));
        assertEquals(new DamageDice(2, 8, 10), PtuTables.dbToDice(8));
        assertEquals(new DamageDice(4, 10, 20), PtuTables.dbToDice(15));
        assertEquals(new DamageDice(4, 10, 25), PtuTables.dbToDice(16));
        assertEquals(new DamageDice(4, 10, 45), PtuTables.dbToDice(20));
    }

    @Test
    void typeChartHandlesWeaknessResistanceAndImmunity() {
        assertEquals(1.5, PtuTables.typeMultiplier("Fire", List.of("Grass")));
        assertEquals(0.5, PtuTables.typeMultiplier("Fire", List.of("Water")));
        assertEquals(0.0, PtuTables.typeMultiplier("Electric", List.of("Ground")));
        assertEquals(1.0, PtuTables.typeMultiplier("Normal", List.of("Normal")));
    }

    @Test
    void dualTypesAccumulateStepsExactlyLikePython() {
        assertEquals(2.0, PtuTables.typeMultiplier("Fire", List.of("Grass", "Steel")));
        assertEquals(0.25, PtuTables.typeMultiplier("Fire", List.of("Water", "Dragon")));
        assertEquals(1.0, PtuTables.typeMultiplier("Fire", List.of("Grass", "Water")));
        assertEquals(0.0, PtuTables.typeMultiplier("Ground", List.of("Fire", "Flying")));
    }

    @Test
    void unknownOrDifferentlyCasedTypesRemainNeutralLikePythonExactKeys() {
        assertEquals(1.0, PtuTables.typeMultiplier("fire", List.of("Grass")));
        assertEquals(1.0, PtuTables.typeMultiplier("Fire", List.of("grass")));
        assertEquals(1.0, PtuTables.typeMultiplier("Mystery", List.of("Water")));
    }
}
