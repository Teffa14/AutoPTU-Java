package io.autoptu.core.rules;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainerFeatureResourceResolutionOracleParityTest {
    @Test
    void availabilityAndConsumptionMatchPinnedPythonDispatcher() throws IOException {
        Path fixturePath = Path.of("build/oracle/trainer-feature-resources.tsv");
        Assumptions.assumeTrue(Files.exists(fixturePath));
        Map<String, Expected> expected = readFixture(fixturePath);

        LinkedHashMap<String, Case> cases = new LinkedHashMap<>();
        cases.put("no_cost", testCase(Map.of(), Map.of("focus", 3)));
        cases.put("empty_cost", testCase(Map.of("resource_cost", Map.of()), Map.of("focus", 3)));
        cases.put("non_dict_cost", testCase(Map.of("resource_cost", List.of("focus")), Map.of("focus", 3)));
        cases.put("exact_balance", testCase(Map.of("resource_cost", Map.of("focus", 3)), Map.of("focus", 3)));
        cases.put("surplus_balance", testCase(Map.of("resource_cost", Map.of("focus", 2)), Map.of("focus", 5)));
        cases.put("insufficient_clamps_on_direct_consume", testCase(Map.of("resource_cost", Map.of("focus", 4)), Map.of("focus", 1)));
        cases.put("missing_resource", testCase(Map.of("resource_cost", Map.of("focus", 1)), Map.of()));
        cases.put("zero_cost_ignored", testCase(Map.of("resource_cost", Map.of("focus", 0)), Map.of("focus", 3)));
        cases.put("negative_cost_ignored", testCase(Map.of("resource_cost", Map.of("focus", -2)), Map.of("focus", 3)));
        cases.put("numeric_string_cost", testCase(Map.of("resource_cost", Map.of("focus", "2")), Map.of("focus", 4)));
        cases.put("float_string_cost_uses_int_like", testCase(Map.of("resource_cost", Map.of("focus", "2.9")), Map.of("focus", 4)));
        cases.put("invalid_cost_ignored", testCase(Map.of("resource_cost", Map.of("focus", "bogus")), Map.of("focus", 4)));
        cases.put("float_balance_uses_direct_int", testCase(Map.of("resource_cost", Map.of("focus", 2)), Map.of("focus", 2.9)));
        cases.put("empty_balance_is_zero", testCase(Map.of("resource_cost", Map.of("focus", 1)), Map.of("focus", "")));
        cases.put("multiple_resources", testCase(
                Map.of("resource_cost", Map.of("focus", 2, "momentum", 1)),
                Map.of("focus", 5, "momentum", 2, "other", 9)
        ));
        cases.put("resource_names_are_case_sensitive", testCase(Map.of("resource_cost", Map.of("Focus", 1)), Map.of("focus", 5)));

        assertEquals(expected.keySet(), cases.keySet());
        for (Map.Entry<String, Case> entry : cases.entrySet()) {
            String name = entry.getKey();
            Case current = entry.getValue();
            boolean available = TrainerFeatureResourceResolution.hasResources(current.feature, current.resources);
            Map<String, Object> consumed = TrainerFeatureResourceResolution.consume(current.feature, current.resources);
            Expected oracle = expected.get(name);
            assertEquals(oracle.available, available ? 1 : 0, name + " availability");
            assertEquals(oracle.after, render(consumed), name + " post-consumption resources");
        }
    }

    private static Case testCase(Map<String, ?> feature, Map<String, ?> resources) {
        return new Case(feature, resources);
    }

    private static String render(Map<String, ?> resources) {
        return new TreeMap<>(resources).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    private static Map<String, Expected> readFixture(Path path) throws IOException {
        LinkedHashMap<String, Expected> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            out.put(parts[0], new Expected(Integer.parseInt(parts[1]), parts.length > 2 ? parts[2] : ""));
        }
        return out;
    }

    private record Expected(int available, String after) {}
    private record Case(Map<String, ?> feature, Map<String, ?> resources) {}
}
