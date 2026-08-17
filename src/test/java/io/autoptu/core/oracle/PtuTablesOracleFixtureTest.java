package io.autoptu.core.oracle;

import io.autoptu.core.model.DamageDice;
import io.autoptu.core.rules.PtuTables;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PtuTablesOracleFixtureTest {
    @Test
    void javaMatchesPinnedPythonPtuTables() throws IOException {
        String fixturePath = System.getProperty("autoptu.ptu.tables.oracle", "").strip();
        Assumptions.assumeFalse(fixturePath.isEmpty(), "Python PTU table fixture path not configured");

        Map<String, String> expected = readFixtures(Path.of(fixturePath));
        Map<String, String> actual = javaResults();
        assertEquals(expected.keySet(), actual.keySet(), "Python and Java PTU table scenario sets differ");
        for (String name : expected.keySet()) {
            assertEquals(expected.get(name), actual.get(name), "PTU table parity failed for scenario: " + name);
        }
    }

    private static Map<String, String> javaResults() {
        Map<String, String> result = new LinkedHashMap<>();
        for (int db : new int[]{1, 2, 3, 8, 15, 16, 20}) {
            DamageDice dice = PtuTables.dbToDice(db);
            result.put("db_" + db, dice.count() + "," + dice.sides() + "," + dice.flat());
        }

        typeCase(result, "fire_grass", "Fire", List.of("Grass"));
        typeCase(result, "fire_water", "Fire", List.of("Water"));
        typeCase(result, "electric_ground", "Electric", List.of("Ground"));
        typeCase(result, "normal_normal", "Normal", List.of("Normal"));
        typeCase(result, "fire_grass_steel", "Fire", List.of("Grass", "Steel"));
        typeCase(result, "fire_water_dragon", "Fire", List.of("Water", "Dragon"));
        typeCase(result, "fire_grass_water", "Fire", List.of("Grass", "Water"));
        typeCase(result, "ground_fire_flying", "Ground", List.of("Fire", "Flying"));
        typeCase(result, "lower_attack_case", "fire", List.of("Grass"));
        typeCase(result, "lower_defense_case", "Fire", List.of("grass"));
        typeCase(result, "unknown_attack", "Mystery", List.of("Water"));
        return result;
    }

    private static void typeCase(
            Map<String, String> result,
            String name,
            String attack,
            List<String> defenses
    ) {
        result.put(name, Double.toString(PtuTables.typeMultiplier(attack, defenses)));
    }

    private static Map<String, String> readFixtures(Path path) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\t", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid oracle fixture line: " + line);
            }
            result.put(parts[0], parts[1]);
        }
        return result;
    }
}
