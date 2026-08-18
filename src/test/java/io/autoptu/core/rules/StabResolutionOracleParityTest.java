package io.autoptu.core.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StabResolutionOracleParityTest {
    @Test
    void matchesPinnedPythonStabDb() throws IOException {
        String oraclePath = System.getProperty("autoptu.stab.oracle", "");
        assumeTrue(!oraclePath.isBlank(), "STAB oracle path not configured");
        Map<String, String> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(oraclePath))) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            expected.put(parts[0], parts.length == 2 ? parts[1] : "");
        }
        assertEquals(expected, generatedFixtures());
    }

    private static Map<String, String> generatedFixtures() {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("same_type", value(5, "Water Gun", "Water", Set.of("Water")));
        rows.put("off_type", value(5, "Water Gun", "Water", Set.of("Fire")));
        rows.put("dual_type_match", value(7, "Flamethrower", "Fire", Set.of("Flying", "Fire")));
        rows.put("case_insensitive", value(4, "Thunder Shock", "Electric", Set.of("eLeCtRiC")));
        rows.put("struggle_no_stab", value(4, "Struggle", "Normal", Set.of("Normal")));
        rows.put("struggle_plus_no_stab", value(6, "Struggle+", "Normal", Set.of("Normal")));
        return rows;
    }

    private static String value(int baseDb, String moveId, String moveType, Set<String> types) {
        return Integer.toString(StabResolution.resolve(baseDb, moveId, moveType, types));
    }
}
