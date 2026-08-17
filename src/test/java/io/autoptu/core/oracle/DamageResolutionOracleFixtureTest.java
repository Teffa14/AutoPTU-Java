package io.autoptu.core.oracle;

import io.autoptu.core.model.AttackModifier;
import io.autoptu.core.model.DamageCheck;
import io.autoptu.core.model.DamageResult;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.DamageResolution;
import io.autoptu.core.rules.PtuTables;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageResolutionOracleFixtureTest {
    @Test
    void javaMatchesPinnedPythonDamagePipeline() throws IOException {
        String fixturePath = System.getProperty("autoptu.damage.oracle", "").strip();
        Assumptions.assumeFalse(fixturePath.isEmpty(), "Python damage oracle fixture path not configured");
        Map<String, String> expected = readFixtures(Path.of(fixturePath));
        Map<String, String> actual = javaResults();
        assertEquals(expected.keySet(), actual.keySet(), "Python and Java damage scenario sets differ");
        for (String name : expected.keySet()) {
            assertEquals(expected.get(name), actual.get(name), "Damage parity failed for scenario: " + name);
        }
    }

    private static Map<String, String> javaResults() {
        Map<String, String> out = new LinkedHashMap<>();
        put(out, "db2_neutral", 1, 2, 10, 5, false, false, 1.0, List.of());
        put(out, "db6_neutral", 42, 6, 12, 7, false, false, 1.0, List.of());
        put(out, "db10_neutral", 1234, 10, 25, 14, false, false, 1.0, List.of());
        put(out, "defense_floor_zero", 7, 2, 1, 999, false, false, 1.0, List.of());
        put(out, "critical_db5", 99, 5, 10, 5, true, false, 1.0, List.of());
        put(out, "critical_sniper_db8", 5, 8, 10, 5, true, true, 1.0, List.of());
        put(out, "fire_vs_grass", 81, 7, 20, 8, false, false, PtuTables.typeMultiplier("Fire", List.of("Grass")), List.of());
        put(out, "fire_vs_water", 81, 7, 20, 8, false, false, PtuTables.typeMultiplier("Fire", List.of("Water")), List.of());
        put(out, "fire_vs_grass_ice", 91, 7, 20, 8, false, false, PtuTables.typeMultiplier("Fire", List.of("Grass", "Ice")), List.of());
        put(out, "fire_vs_water_dragon", 91, 7, 20, 8, false, false, PtuTables.typeMultiplier("Fire", List.of("Water", "Dragon")), List.of());
        put(out, "electric_immunity_ground", 27, 8, 18, 8, false, false, PtuTables.typeMultiplier("Electric", List.of("Ground")), List.of());
        put(out, "flat_then_scalar", 314, 4, 10, 5, false, false, 1.0, List.of(
                AttackModifier.flat("fixture-flat-3", 3.0),
                AttackModifier.scalar("fixture-scalar-0.5", 0.5),
                AttackModifier.scalar("fixture-scalar-1.5", 1.5)
        ));
        put(out, "scalar_rounding_order", 315, 9, 13, 9, false, false, 1.0, List.of(
                AttackModifier.scalar("fixture-scalar-0.66", 0.66),
                AttackModifier.scalar("fixture-scalar-1.5", 1.5)
        ));
        put(out, "crit_with_modifiers", 888, 11, 22, 12, true, false, 1.0, List.of(
                AttackModifier.flat("fixture-flat-5", 5.0),
                AttackModifier.scalar("fixture-scalar-0.5", 0.5)
        ));
        return out;
    }

    private static void put(
            Map<String, String> out,
            String name,
            long seed,
            int db,
            int attack,
            int defense,
            boolean crit,
            boolean sniper,
            double typeMultiplier,
            List<AttackModifier> modifiers
    ) {
        DamageResult result = DamageResolution.resolve(
                new PythonRandom(seed),
                new DamageCheck(db, attack, defense, crit, sniper, typeMultiplier, modifiers)
        );
        out.put(name, String.format(
                Locale.ROOT,
                "%d,%dd%d+%d,%d,%d,%d,%d,%d,%.6f,%d",
                db,
                result.dice().count(), result.dice().sides(), result.dice().flat(),
                result.damageRoll(), result.criticalExtraRoll(), attack, defense,
                result.preTypeDamage(), typeMultiplier, result.damage()
        ));
    }

    private static Map<String, String> readFixtures(Path path) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid damage fixture: " + line);
            out.put(parts[0], parts[1]);
        }
        return out;
    }
}
