package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AccuracyHelperContractOracleParityTest {
    @Test
    void matchesPinnedPythonAccuracyHelperBehavior() throws IOException {
        String oracle = System.getenv("AUTOPTU_ACCURACY_HELPER_CONTRACT_ORACLE");
        assumeTrue(oracle != null && !oracle.isBlank(), "Accuracy helper contract fixture not configured");

        List<String> lines = Files.readAllLines(Path.of(oracle));
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            expected.put(fields[0], Integer.parseInt(fields[1]));
        }

        assertEquals(1, expected.get("focused_helper_defined"));
        assertEquals(1, expected.get("chronicler_helper_defined"));
        assertEquals(1, expected.get("focused_fallback_is_one"));
        assertEquals(1, expected.get("chronicler_is_optional"));

        assertEquals(1, expected.get("focused_requires_training_effect"));
        assertEquals(1, expected.get("focused_checks_duelist_feature"));
        assertEquals(1, expected.get("focused_checks_any_controller_tag"));
        assertEquals(1, expected.get("focused_requires_tagged_defender_for_duelist"));
        assertEquals(1, expected.get("focused_uses_ceil_half_momentum"));
        assertEquals(1, expected.get("focused_default_bonus_is_one"));

        assertEquals(1, expected.get("chronicler_iterates_targeted_profiling"));
        assertEquals(1, expected.get("chronicler_expiry_is_strictly_after_round"));
        assertEquals(1, expected.get("chronicler_removes_expired_entries"));
        assertEquals(1, expected.get("chronicler_source_controller_falls_back_to_attacker"));
        assertEquals(1, expected.get("chronicler_requires_profile_match"));
        assertEquals(1, expected.get("chronicler_adds_two_per_match"));
    }
}
