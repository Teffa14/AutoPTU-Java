package io.autoptu.core.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RiderAgilityTrainingResolutionOracleParityTest {
    @Test
    void matchesPinnedPythonOracle() throws IOException {
        String oraclePath = System.getProperty("autoptu.rider.agility.oracle");
        if (oraclePath == null || oraclePath.isBlank()) {
            return;
        }

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertEquals(
                "name\tactor_id\tmounted_pairs\texisting_actor_ids\trider_feature_actor_ids\tagility_training_actor_ids\texpected_doubled",
                lines.getFirst()
        );

        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            boolean actual = RiderAgilityTrainingResolution.doubled(
                    parts[1],
                    parsePairs(parts[2]),
                    parseIds(parts[3]),
                    parseIds(parts[4]),
                    parseIds(parts[5])
            );
            assertEquals("1".equals(parts[6]), actual, parts[0]);
        }
    }

    private static Map<String, String> parsePairs(String text) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return result;
        for (String pair : text.split(",")) {
            String[] parts = pair.split(":", 2);
            if (parts.length == 2) result.put(parts[0], parts[1]);
        }
        return result;
    }

    private static List<String> parseIds(String text) {
        ArrayList<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        for (String value : text.split(",")) {
            if (!value.isBlank()) result.add(value);
        }
        return result;
    }
}
