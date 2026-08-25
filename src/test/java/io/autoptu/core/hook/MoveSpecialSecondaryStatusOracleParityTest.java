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

final class MoveSpecialSecondaryStatusOracleParityTest {
    private record Scenario(String text, int roll) {}

    @Test
    void matchesPinnedPythonGenericSecondaryStatusSemantics() throws IOException {
        String fixturePath = System.getProperty("autoptu.move.special.secondary.status.oracle");
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
                Map.entry("threshold_burn_hit", new Scenario("Burns the target on 18+.", 18)),
                Map.entry("threshold_burn_miss", new Scenario("Burns the target on 18+.", 17)),
                Map.entry("threshold_flinch", new Scenario("Flinches on a 19+.", 19)),
                Map.entry("past_tense_paralyzed_quirk", new Scenario("Paralyzed target on 18+.", 20)),
                Map.entry("always_poison", new Scenario("Poisons the target.", 1)),
                Map.entry("always_freeze", new Scenario("Freezes the target.", 1)),
                Map.entry("past_tense_frozen_quirk", new Scenario("Frozen target.", 1)),
                Map.entry("even_paralyze_hit", new Scenario("Paralyzes the target on an even-numbered roll.", 12)),
                Map.entry("even_paralyze_miss", new Scenario("Paralyzes the target on an even-numbered roll.", 11)),
                Map.entry("falls_asleep", new Scenario("The target falls asleep.", 1)),
                Map.entry("threshold_then_sleep", new Scenario("Burns the target on 18+. The target falls asleep.", 18)),
                Map.entry("nbsp_normalization", new Scenario("Burns\u00a0the target on 18+.", 18))
        );

        assertEquals(expected.keySet(), scenarios.keySet());
        for (Map.Entry<String, Scenario> entry : scenarios.entrySet()) {
            String name = entry.getKey();
            Scenario scenario = entry.getValue();
            String actual = encode(MoveSpecialSecondaryStatusResolution.resolve(scenario.text(), scenario.roll()));
            assertEquals(expected.get(name), actual, name);
        }
    }

    private static String encode(List<MoveSpecialSecondaryStatusResolution.StatusRequest> requests) {
        return requests.stream()
                .map(request -> request.status() + "|" + request.effect() + "|"
                        + (request.remaining() == null ? "" : request.remaining()))
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
    }
}
