package io.autoptu.core.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StatusSkipExceptionOracleParityTest {
    @Test
    void matchesPinnedPythonStatusControllerExceptions() throws IOException {
        String oracle = System.getProperty("autoptu.status.skip.exception.oracle");
        assumeTrue(oracle != null && !oracle.isBlank(), "status-skip exception oracle not configured");

        Map<String, String> expected = readOracle(Path.of(oracle));
        Map<String, String> actual = new LinkedHashMap<>();
        actual.put("supreme_flinch", StatusSkipExceptionResolution.resolve(
                "Flinch", "Supreme Concentration", "Thunderbolt", false).stableKey());
        actual.put("supreme_sleep_not_covered", StatusSkipExceptionResolution.resolve(
                "Sleep", "Supreme Concentration", "Thunderbolt", false).stableKey());
        actual.put("duelist_confused", StatusSkipExceptionResolution.resolve(
                "Confused", "", "", true).stableKey());
        actual.put("duelist_flinch_not_covered", StatusSkipExceptionResolution.resolve(
                "Flinch", "", "", true).stableKey());
        actual.put("supreme_priority_when_both_apply", StatusSkipExceptionResolution.resolve(
                "Confusion", "Supreme-Concentration", "Psychic", true).stableKey());

        assertEquals(expected, actual);
    }

    private static Map<String, String> readOracle(Path path) throws IOException {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            rows.put(parts[0], parts[1]);
        }
        return rows;
    }
}
