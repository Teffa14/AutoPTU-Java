package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FocusedTrainingAccuracyBonusOracleParityTest {
    @Test
    void matchesPinnedPythonFocusedTrainingContract() throws IOException {
        String oracle = System.getenv("AUTOPTU_ACCURACY_HELPER_CONTRACT_ORACLE");
        assumeTrue(oracle != null && !oracle.isBlank(), "Accuracy helper contract fixture not configured");

        List<String> lines = Files.readAllLines(Path.of(oracle));
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            expected.put(fields[0], Integer.parseInt(fields[1]));
        }

        assertEquals(1, expected.get("focused_requires_training_effect"));
        assertEquals(1, expected.get("focused_checks_duelist_feature"));
        assertEquals(1, expected.get("focused_checks_any_controller_tag"));
        assertEquals(1, expected.get("focused_requires_tagged_defender_for_duelist"));
        assertEquals(1, expected.get("focused_uses_ceil_half_momentum"));
        assertEquals(1, expected.get("focused_default_bonus_is_one"));

        assertThrows(IllegalArgumentException.class, () -> FocusedTrainingAccuracyBonusResolution.resolve(null));
        assertEquals(0, resolve(false, false, false, false, 0));
        assertEquals(1, resolve(true, false, false, false, 0));
        assertEquals(1, resolve(true, true, false, false, 8));
        assertEquals(0, resolve(true, true, true, false, 8));
        assertEquals(0, resolve(true, true, true, true, 0));
        assertEquals(2, resolve(true, true, true, true, 4));
        assertEquals(3, resolve(true, true, true, true, 5));
        assertEquals(0, resolve(true, true, true, true, -3));
    }

    private static int resolve(
            boolean focusedTrainingActive,
            boolean duelistFeature,
            boolean anyControllerTag,
            boolean defenderTagged,
            int momentum
    ) {
        return FocusedTrainingAccuracyBonusResolution.resolve(new FocusedTrainingAccuracyBonusResolution.Input(
                focusedTrainingActive,
                duelistFeature,
                anyControllerTag,
                defenderTagged,
                momentum));
    }
}
