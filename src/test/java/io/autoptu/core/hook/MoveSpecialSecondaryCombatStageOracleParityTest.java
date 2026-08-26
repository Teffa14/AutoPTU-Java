package io.autoptu.core.hook;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MoveSpecialSecondaryCombatStageOracleParityTest {
    private record Scenario(String text, int roll) {}

    @Test
    void matchesPinnedPythonGenericSecondaryCombatStageSemantics() throws IOException {
        String fixturePath = System.getProperty("autoptu.move.special.secondary.combat.stage.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Path fixture = Path.of(fixturePath);
        Assumptions.assumeTrue(Files.exists(fixture));

        Map<String, String> expected = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(fixture);
        assertEquals("name\texpected", lines.getFirst());
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            expected.put(parts[0], parts[1]);
        }

        Map<String, Scenario> scenarios = Map.ofEntries(
                Map.entry("raise_target_threshold_hit", new Scenario("Raises the target's Attack by +2 Combat Stage on 18+.", 18)),
                Map.entry("raise_target_threshold_miss", new Scenario("Raises the target's Attack by +2 Combat Stage on 18+.", 17)),
                Map.entry("lower_target", new Scenario("Lowers the target's Defense by -1 CS.", 1)),
                Map.entry("raise_user_multi", new Scenario("Raises the user's Special Attack / Speed by +1 Combat Stage.", 1)),
                Map.entry("lower_user_multi", new Scenario("Lowers the user's Special Defense and Accuracy by -2 Combat Stage.", 1)),
                Map.entry("alt_target_lower", new Scenario("Target's Evasion is lowered by -2 Combat Stages.", 1)),
                Map.entry("alt_raise", new Scenario("Raises the user's Accuracy 1 Combat Stage.", 1)),
                Map.entry("simple_all_targets_lower", new Scenario("All legal targets have their Speed lowered by -1 Combat Stage.", 1)),
                Map.entry("nbsp_normalization", new Scenario("Raises\u00a0the target's Attack by +1 Combat Stage.", 1)),
                Map.entry("dedupe_stats", new Scenario("Raises the user's Attack / Attack by +1 Combat Stage.", 1))
        );

        assertEquals(expected.keySet(), scenarios.keySet());
        for (Map.Entry<String, Scenario> entry : scenarios.entrySet()) {
            Scenario scenario = entry.getValue();
            String actual = encode(MoveSpecialSecondaryCombatStageResolution.resolve(
                    scenario.text(), scenario.roll()));
            assertEquals(expected.get(entry.getKey()), actual, entry.getKey());
        }
    }

    private static String encode(List<MoveSpecialSecondaryCombatStageResolution.StageRequest> requests) {
        return requests.stream()
                .map(request -> request.target().name().toLowerCase() + "|" + request.stat() + "|" + request.delta())
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
    }
}
