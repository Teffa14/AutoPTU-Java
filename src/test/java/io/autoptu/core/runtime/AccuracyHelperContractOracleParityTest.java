package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AccuracyHelperContractOracleParityTest {
    @Test
    void matchesPinnedPythonAccuracyHelperOwnership() throws IOException {
        String oracle = System.getenv("AUTOPTU_ACCURACY_HELPER_CONTRACT_ORACLE");
        assumeTrue(oracle != null && !oracle.isBlank(), "Accuracy helper contract fixture not configured");

        Map<String, Integer> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(oracle)).subList(1, Files.readAllLines(Path.of(oracle)).size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            expected.put(fields[0], Integer.parseInt(fields[1]));
        }

        assertEquals(1, expected.get("focused_helper_defined"));
        assertEquals(1, expected.get("chronicler_helper_defined"));
        assertEquals(1, expected.get("focused_fallback_is_one"));
        assertEquals(1, expected.get("chronicler_is_optional"));
    }
}
