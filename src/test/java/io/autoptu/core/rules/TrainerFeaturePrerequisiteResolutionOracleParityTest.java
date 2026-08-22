package io.autoptu.core.rules;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainerFeaturePrerequisiteResolutionOracleParityTest {
    @Test
    void selectionAndPrerequisitesMatchPinnedPythonDispatcher() throws IOException {
        Path fixturePath = Path.of("build/oracle/trainer-feature-prerequisites.tsv");
        Assumptions.assumeTrue(Files.exists(fixturePath));
        Fixture fixture = readFixture(fixturePath);

        List<?> features = List.of(
                "Quick Switch",
                Map.of("feature_id", "alpha", "name", "Ignored Name")
        );
        List<?> edges = List.of(
                "Quick Switch",
                Map.of("id", "edge-two", "name", "Ignored Edge Name")
        );
        List<?> knownFeatures = List.of("Class Gift", Map.of("name", "alpha"));

        String actualSelection = TrainerFeaturePrerequisiteResolution.selectFeatureIdentities(
                        features, edges, knownFeatures
                ).stream()
                .map(entry -> entry.identifier() + ":" + entry.runtimeKind())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        assertEquals(fixture.selection(), actualSelection);

        Map<String, Object> trainerClass = new LinkedHashMap<>();
        trainerClass.put("class_id", "Ace Trainer");
        trainerClass.put("subclass_id", "Commander");
        trainerClass.put("level", "4.9");
        List<String> knownIds = List.of("quick-switch", "alpha", "edge-two", "class-gift");

        Map<String, Map<String, ?>> cases = new LinkedHashMap<>();
        cases.put("baseline", Map.of());
        cases.put("min_level_pass", Map.of("min_trainer_level", 4));
        cases.put("min_level_fail", Map.of("min_trainer_level", 5));
        cases.put("level_required_float_string", Map.of("level_required", "4.9"));
        cases.put("required_class_casefold", Map.of("required_classes", List.of(" ACE TRAINER ")));
        cases.put("required_class_fail", Map.of("required_classes", List.of("researcher")));
        cases.put("required_subclass_pass", Map.of("required_subclasses", " commander "));
        cases.put("required_subclass_fail", Map.of("required_subclasses", "ranger"));
        cases.put("required_feature_pass", Map.of("required_features", List.of("alpha", "class-gift")));
        cases.put("required_feature_space_does_not_hyphenate", Map.of("required_features", List.of("Class Gift")));
        cases.put("nested_pass", Map.of("prerequisites", Map.of(
                "level", 4,
                "class", "ace trainer",
                "subclass", "commander",
                "features", "alpha"
        )));
        cases.put("nested_level_fail", Map.of("prerequisites", Map.of("min_trainer_level", 5)));
        cases.put("nested_feature_fail", Map.of("prerequisites", Map.of("feature", "missing")));

        assertEquals(fixture.prerequisites().keySet(), cases.keySet());
        for (Map.Entry<String, Map<String, ?>> entry : cases.entrySet()) {
            boolean actual = TrainerFeaturePrerequisiteResolution.prerequisitesMet(
                    trainerClass,
                    entry.getValue(),
                    knownIds
            );
            assertEquals(fixture.prerequisites().get(entry.getKey()), actual ? 1 : 0, entry.getKey());
        }
    }

    private static Fixture readFixture(Path path) throws IOException {
        String selection = "";
        LinkedHashMap<String, Integer> prerequisites = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t");
            if (parts[0].equals("selection")) {
                selection = parts.length > 1 ? parts[1] : "";
            } else if (parts[0].equals("prerequisite")) {
                prerequisites.put(parts[1], Integer.parseInt(parts[2]));
            }
        }
        return new Fixture(selection, prerequisites);
    }

    private record Fixture(String selection, Map<String, Integer> prerequisites) {}
}
