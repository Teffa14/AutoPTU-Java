package io.autoptu.core.rules;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrainerFeatureUsageResolutionOracleParityTest {
    @Test
    void usageAndCooldownMutationMatchPinnedPythonDispatcher() throws IOException {
        Path fixturePath = Path.of("build/oracle/trainer-feature-usage.tsv");
        Assumptions.assumeTrue(Files.exists(fixturePath));
        Map<String, String> expected = readFixture(fixturePath);

        LinkedHashMap<String, Case> cases = new LinkedHashMap<>();
        cases.put("new_usage_from_name", testCase(Map.of("name", "Quick Draw"), Map.of(), 4, null));
        cases.put("increments_existing_usage", testCase(
                Map.of("feature_id", "steady-hand"),
                usage("steady-hand", Map.of("uses_total", 2, "uses_round_4", 1, "legacy", 7)),
                4,
                null
        ));
        cases.put("tracks_actor_round", testCase(
                Map.of("feature_id", "steady-hand"),
                usage("steady-hand", Map.of("uses_total", 1)),
                4,
                "mon-1"
        ));
        cases.put("empty_actor_does_not_track", testCase(
                Map.of("feature_id", "steady-hand"),
                usage("steady-hand", Map.of("uses_total", 1)),
                4,
                ""
        ));
        cases.put("cooldown_rounds", testCase(Map.of("feature_id", "burst", "cooldown_rounds", 2), Map.of(), 4, null));
        cases.put("cooldown_fallback", testCase(Map.of("feature_id", "burst", "cooldown", "3.9"), Map.of(), 4, null));
        cases.put("cooldown_rounds_none_overrides_fallback", testCase(
                nullableFeature("burst", null, 5),
                Map.of(),
                4,
                null
        ));
        cases.put("nonpositive_cooldown_preserves_existing", testCase(
                Map.of("feature_id", "burst", "cooldown_rounds", 0),
                usage("burst", Map.of("cooldown_until", 9)),
                4,
                null
        ));
        cases.put("cooldown_overwrites_existing", testCase(
                Map.of("feature_id", "burst", "cooldown_rounds", 2),
                usage("burst", Map.of("cooldown_until", 99)),
                4,
                null
        ));
        cases.put("feature_id_has_priority", testCase(
                Map.of("feature_id", "primary", "id", "secondary", "name", "Display Name"),
                Map.of(),
                2,
                null
        ));
        cases.put("id_fallback", testCase(Map.of("id", "Second Choice", "name", "Display Name"), Map.of(), 2, null));
        cases.put("default_feature_id", testCase(Map.of(), Map.of(), 2, null));
        cases.put("numeric_usage_values", testCase(
                Map.of("feature_id", "numbers"),
                usage("numbers", Map.of("uses_total", "2", "uses_round_5", 2.9)),
                5,
                null
        ));
        cases.put("falsey_usage_values", testCase(
                Map.of("feature_id", "zeros"),
                usage("zeros", Map.of("uses_total", "", "uses_round_0", 0)),
                0,
                null
        ));
        LinkedHashMap<String, Map<String, ?>> multiUsage = new LinkedHashMap<>();
        multiUsage.put("other", Map.of("uses_total", 8));
        multiUsage.put("used", Map.of("uses_total", 1));
        cases.put("preserves_other_features", testCase(Map.of("feature_id", "used"), multiUsage, 3, null));

        assertEquals(expected.keySet(), cases.keySet());
        for (Map.Entry<String, Case> entry : cases.entrySet()) {
            Case current = entry.getValue();
            Map<String, Map<String, Object>> after = TrainerFeatureUsageResolution.markUse(
                    current.feature,
                    current.usage,
                    current.currentRound,
                    current.actorId
            );
            assertEquals(expected.get(entry.getKey()), render(after), entry.getKey());
        }
    }

    @Test
    void directUsageCountersUsePythonIntSemantics() {
        assertThrows(IllegalArgumentException.class, () -> TrainerFeatureUsageResolution.markUse(
                Map.of("feature_id", "bad"),
                usage("bad", Map.of("uses_total", "2.9")),
                1,
                null
        ));
    }

    private static Case testCase(
            Map<String, ?> feature,
            Map<String, ? extends Map<String, ?>> usage,
            int currentRound,
            String actorId
    ) {
        return new Case(feature, usage, currentRound, actorId);
    }

    private static Map<String, Map<String, ?>> usage(String featureId, Map<String, ?> info) {
        return Map.of(featureId, info);
    }

    private static Map<String, Object> nullableFeature(String featureId, Object cooldownRounds, Object cooldown) {
        LinkedHashMap<String, Object> feature = new LinkedHashMap<>();
        feature.put("feature_id", featureId);
        feature.put("cooldown_rounds", cooldownRounds);
        feature.put("cooldown", cooldown);
        return feature;
    }

    private static String render(Map<String, ? extends Map<String, ?>> usage) {
        return new TreeMap<>(usage).entrySet().stream()
                .map(entry -> entry.getKey() + "[" + new TreeMap<>(entry.getValue()).entrySet().stream()
                        .map(value -> value.getKey() + "=" + value.getValue())
                        .collect(Collectors.joining(",")) + "]")
                .collect(Collectors.joining(";"));
    }

    private static Map<String, String> readFixture(Path path) throws IOException {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            out.put(parts[0], parts.length > 1 ? parts[1] : "");
        }
        return out;
    }

    private record Case(
            Map<String, ?> feature,
            Map<String, ? extends Map<String, ?>> usage,
            int currentRound,
            String actorId
    ) {}
}
