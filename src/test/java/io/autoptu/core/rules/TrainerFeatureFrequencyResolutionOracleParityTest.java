package io.autoptu.core.rules;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainerFeatureFrequencyResolutionOracleParityTest {
    @Test
    void frequencyLimitsCooldownsAndUsageMatchPinnedPythonDispatcher() throws IOException {
        Path fixturePath = Path.of("build/oracle/trainer-feature-frequency.tsv");
        Assumptions.assumeTrue(Files.exists(fixturePath));
        Map<String, Expected> expected = readFixture(fixturePath);

        LinkedHashMap<String, Case> cases = new LinkedHashMap<>();
        cases.put("baseline_at_will", testCase(Map.of()));
        cases.put("daily_default", testCase(Map.of("frequency", "Daily")));
        cases.put("scene_default", testCase(Map.of("frequency", " scene ")));
        cases.put("encounter_default", testCase(Map.of("frequency", "ENCOUNTER")));
        cases.put("eot_default", testCase(Map.of("frequency", "EOT")));
        cases.put("round_default", testCase(Map.of("frequency", "Round")));
        cases.put("turn_default", testCase(Map.of("frequency", "Turn")));
        cases.put("x_round_default", testCase(Map.of("frequency", "x/round")));
        cases.put("per_round_dash_default", testCase(Map.of("frequency", "per-round")));
        cases.put("per_round_space_default", testCase(Map.of("frequency", "per round")));
        cases.put("two_per_round", testCase(Map.of("frequency", "2/round")));
        cases.put("three_per_turn", testCase(Map.of("frequency", "3 / turn")));
        cases.put("four_per_scene", testCase(Map.of("frequency", "4/scene")));
        cases.put("five_per_daily", testCase(Map.of("frequency", "5 / daily")));
        cases.put("six_per_encounter", testCase(Map.of("frequency", "6/encounter")));
        cases.put("explicit_total_precedence", testCase(Map.of("frequency", "Daily", "max_uses", 3)));
        cases.put("explicit_round_precedence", testCase(Map.of("frequency", "EOT", "uses_per_round", 4)));
        cases.put("explicit_both", testCase(Map.of("frequency", "2/round", "max_uses", 7, "uses_per_round", 5)));
        cases.put("negative_daily_defaults", testCase(Map.of("frequency", "Daily", "max_uses", -2)));
        cases.put("negative_round_defaults", testCase(Map.of("frequency", "Round", "uses_per_round", -4)));
        cases.put("zero_per_scene", testCase(Map.of("frequency", "0/scene")));
        cases.put("unknown_frequency", testCase(Map.of("frequency", "Special")));
        cases.put("numeric_string_limits", testCase(Map.of("max_uses", "3", "uses_per_round", "2")));
        cases.put("cooldown_before_blocks", testCase(Map.of()).usage(Map.of("cooldown_until", 4)).round(3));
        cases.put("cooldown_equal_blocks", testCase(Map.of()).usage(Map.of("cooldown_until", 3)).round(3));
        cases.put("cooldown_after_allows", testCase(Map.of()).usage(Map.of("cooldown_until", 2)).round(3));
        cases.put("total_below_allows", testCase(Map.of("frequency", "Daily", "max_uses", 2)).usage(Map.of("uses_total", 1)));
        cases.put("total_equal_blocks", testCase(Map.of("frequency", "Daily", "max_uses", 2)).usage(Map.of("uses_total", 2)));
        cases.put("total_above_blocks", testCase(Map.of("frequency", "Daily", "max_uses", 2)).usage(Map.of("uses_total", 3)));
        cases.put("round_below_allows", testCase(Map.of("frequency", "2/round")).usage(Map.of("uses_round_3", 1)));
        cases.put("round_equal_blocks", testCase(Map.of("frequency", "2/round")).usage(Map.of("uses_round_3", 2)));
        cases.put("other_round_ignored", testCase(Map.of("frequency", "2/round")).usage(Map.of("uses_round_2", 99)));
        cases.put("cooldown_precedes_usage", testCase(Map.of("frequency", "Daily")).usage(Map.of("cooldown_until", 3, "uses_total", 99)));
        cases.put("string_usage_coercion", testCase(Map.of("frequency", "2/round", "max_uses", 3)).usage(Map.of("uses_total", "2", "uses_round_3", "1")));

        assertEquals(expected.keySet(), cases.keySet());
        for (Map.Entry<String, Case> entry : cases.entrySet()) {
            Case current = entry.getValue();
            TrainerFeatureFrequencyResolution.Limits actualLimits = TrainerFeatureFrequencyResolution.limits(current.feature);
            boolean actualAvailable = TrainerFeatureFrequencyResolution.isAvailable(
                    current.feature,
                    current.usage,
                    current.round
            );
            Expected oracle = expected.get(entry.getKey());
            assertEquals(oracle.totalLimit, actualLimits.totalLimit(), entry.getKey() + " total limit");
            assertEquals(oracle.roundLimit, actualLimits.roundLimit(), entry.getKey() + " round limit");
            assertEquals(oracle.available, actualAvailable ? 1 : 0, entry.getKey() + " availability");
        }
    }

    private static Case testCase(Map<String, ?> feature) {
        return new Case(feature, Map.of(), 3);
    }

    private static Map<String, Expected> readFixture(Path path) throws IOException {
        LinkedHashMap<String, Expected> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t");
            out.put(parts[0], new Expected(
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            ));
        }
        return out;
    }

    private record Expected(int totalLimit, int roundLimit, int available) {}

    private record Case(Map<String, ?> feature, Map<String, ?> usage, int round) {
        Case usage(Map<String, ?> usage) {
            return new Case(feature, usage, round);
        }

        Case round(int round) {
            return new Case(feature, usage, round);
        }
    }
}
