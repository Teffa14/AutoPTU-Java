package io.autoptu.core.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MoveFrequencyOracleParityTest {
    @Test
    void matchesPinnedPythonFrequencyParsing() throws IOException {
        String oraclePath = System.getProperty("autoptu.move.frequency.oracle", "");
        assumeTrue(!oraclePath.isBlank(), "move frequency oracle path not configured");
        Map<String, String> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(oraclePath))) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            expected.put(parts[0], parts.length == 2 ? parts[1] : "");
        }
        assertEquals(expected, generatedFixtures());
    }

    private static Map<String, String> generatedFixtures() {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("blank", value(null));
        rows.put("at_will", value("At-Will"));
        rows.put("scene", value("Scene"));
        rows.put("scene_x3", value("Scene x3"));
        rows.put("scene_whitespace_case", value("  SCENE   x  2  "));
        rows.put("daily", value("Daily"));
        rows.put("daily_x2", value("Daily x2"));
        rows.put("eot", value("EOT"));
        return rows;
    }

    private static String value(String raw) {
        return MoveFrequency.parse(raw)
                .map(definition -> definition.slug()
                        + "|" + definition.limit()
                        + "|" + definition.scope().name().toLowerCase(Locale.ROOT))
                .orElse("none");
    }
}
