package io.autoptu.core.oracle;

import io.autoptu.core.model.AccuracyCheck;
import io.autoptu.core.model.AccuracyResult;
import io.autoptu.core.rules.Accuracy;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccuracyOracleFixtureTest {
    @Test
    void javaMatchesPinnedPythonAccuracyOracle() throws IOException {
        String fixturePath = System.getProperty("autoptu.accuracy.oracle", "").strip();
        Assumptions.assumeFalse(fixturePath.isEmpty(), "Python accuracy oracle fixture path not configured");

        Map<String, String> expected = readFixtures(Path.of(fixturePath));
        Map<String, String> actual = javaResults();
        assertEquals(expected.keySet(), actual.keySet(), "Python and Java accuracy scenario sets differ");
        for (String name : expected.keySet()) {
            assertEquals(expected.get(name), actual.get(name), "Accuracy parity failed for scenario: " + name);
        }
    }

    private static Map<String, String> javaResults() {
        Map<String, String> result = new LinkedHashMap<>();
        put(result, "natural_one_standard", check(2, 0, 0, 1, null, 20, false, false));
        put(result, "minimum_needed_roll_two", check(2, 0, 0, 2, null, 20, false, false));
        put(result, "natural_twenty_high_needed", check(25, 0, 0, 20, null, 20, false, false));
        put(result, "high_needed_roll_nineteen", check(25, 0, 0, 19, null, 20, false, false));
        put(result, "stage_clamp_positive", check(10, 3, 9, 7, null, 20, false, false));
        put(result, "stage_clamp_negative", check(2, 0, -9, 8, null, 20, false, false));
        put(result, "no_guard_melee", check(6, 5, 0, 6, null, 20, true, false));
        put(result, "no_guard_ranged_does_not_remove_evasion", check(6, 5, 0, 6, null, 20, false, false));
        put(result, "ac_none_automatic_natural_one", check(null, 9, 0, 1, null, 20, false, false));
        put(result, "ac_none_automatic_crit18", check(null, 9, 0, 18, null, 18, false, false));
        put(result, "blur_ac_none", check(null, 7, 0, 5, null, 20, false, true));
        put(result, "blur_natural_one", check(null, 7, 0, 1, null, 20, false, true));
        put(result, "reroll_natural_one_to_hit", check(10, 0, 0, 1, 15, 20, false, false));
        put(result, "reroll_miss_to_crit", check(10, 0, 0, 5, 20, 20, false, false));
        put(result, "accuracy_components", check(10, 3, 5, 8, null, 20, false, false));
        put(result, "crit18_hit", check(10, 0, 0, 18, null, 18, false, false));
        put(result, "crit_threshold_on_miss", check(20, 0, 0, 18, null, 18, false, false));
        return result;
    }

    private static void put(Map<String, String> values, String name, AccuracyCheck check) {
        AccuracyResult resolved = Accuracy.resolve(check);
        double probability = Accuracy.hitProbability(check);
        values.put(
                name,
                String.format(
                        Locale.ROOT,
                        "%s,%s,%d,%d,%.6f",
                        resolved.hit(),
                        resolved.crit(),
                        resolved.roll(),
                        resolved.needed(),
                        probability
                )
        );
    }

    private static AccuracyCheck check(
            Integer ac,
            int evasion,
            int stage,
            int roll,
            Integer reroll,
            int critRange,
            boolean noGuard,
            boolean blur
    ) {
        return new AccuracyCheck(ac, evasion, stage, roll, reroll, critRange, noGuard, blur);
    }

    private static Map<String, String> readFixtures(Path path) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\t", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid accuracy oracle fixture line: " + line);
            }
            result.put(parts[0], parts[1]);
        }
        return result;
    }
}
