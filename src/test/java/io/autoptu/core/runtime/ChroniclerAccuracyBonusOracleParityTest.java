package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ChroniclerAccuracyBonusOracleParityTest {
    @Test
    void matchesPinnedPythonBonusExpiryStackingAndControllerFallback() throws IOException {
        String oracle = System.getenv("AUTOPTU_CHRONICLER_ACCURACY_BONUS_ORACLE");
        assumeTrue(oracle != null && !oracle.isBlank(), "Chronicler Accuracy fixture not configured");

        Map<String, Expected> expected = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(Path.of(oracle));
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            expected.put(fields[0], new Expected(
                    Integer.parseInt(fields[1]),
                    Integer.parseInt(fields[2]),
                    fields.length > 3 ? fields[3] : ""
            ));
        }

        assertCase(expected, "baseline", 3, "trainer-a", List.of(), Set.of());
        assertCase(expected, "single_match", 3, "trainer-a", List.of(Map.of()), Set.of("trainer-a"));
        assertCase(expected, "stacked_matches", 3, "trainer-a", List.of(Map.of(), Map.of()), Set.of("trainer-a"));
        assertCase(expected, "nonmatch", 3, "trainer-a", List.of(Map.of()), Set.of());
        assertCase(expected, "fallback_controller_match", 3, "trainer-a",
                List.of(Map.of("source_controller", "")), Set.of("trainer-a"));
        assertCase(expected, "explicit_controller_match", 3, "trainer-a",
                List.of(Map.of("source_controller", "trainer-b")), Set.of("trainer-b"));
        assertCase(expected, "same_round_not_expired", 3, "trainer-a",
                List.of(Map.of("expires_round", 3)), Set.of("trainer-a"));
        assertCase(expected, "next_round_expired", 4, "trainer-a",
                List.of(Map.of("expires_round", 3)), Set.of("trainer-a"));
        assertCase(expected, "mixed_expired_and_live", 4, "trainer-a",
                List.of(
                        Map.of("expires_round", 2),
                        Map.of("expires_round", 4),
                        Map.of("source_controller", "trainer-b")
                ),
                Set.of("trainer-a", "trainer-b"));
    }

    private static void assertCase(
            Map<String, Expected> expectedByName,
            String name,
            int currentRound,
            String attackerControllerId,
            List<Map<String, ?>> payloads,
            Set<String> matchingControllers
    ) {
        Expected expected = expectedByName.get(name);
        if (expected == null) throw new AssertionError("Missing oracle case: " + name);

        TemporaryEffectStore store = new TemporaryEffectStore();
        for (Map<String, ?> payload : payloads) {
            store.add("targeted_profiling", payload);
        }
        ArrayList<String> seen = new ArrayList<>();
        Set<String> matches = new LinkedHashSet<>(matchingControllers);
        int bonus = ChroniclerAccuracyBonusResolution.resolve(
                store,
                currentRound,
                attackerControllerId,
                sourceControllerId -> {
                    seen.add(sourceControllerId);
                    return matches.contains(sourceControllerId);
                }
        );

        assertEquals(expected.bonus(), bonus, name + " bonus");
        assertEquals(expected.remaining(), store.count("targeted_profiling"), name + " remaining");
        assertEquals(expected.controllersSeen(), String.join(",", seen), name + " controllers seen");
    }

    private record Expected(int bonus, int remaining, String controllersSeen) {
    }
}
