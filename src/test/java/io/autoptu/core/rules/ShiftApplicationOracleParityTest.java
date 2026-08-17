package io.autoptu.core.rules;

import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ShiftApplicationOracleParityTest {
    @Test
    void matchesPinnedPythonOracle() throws IOException {
        String oraclePath = System.getProperty("autoptu.shift.application.oracle", "");
        assumeTrue(!oraclePath.isBlank(), "shift-application oracle path not configured");
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
        ActionBudget budget = new ActionBudget();
        var result = ShiftApplication.apply("actor", new GridCoord(1, 1), new GridCoord(2, 1),
                Set.of(new GridCoord(2, 1)), budget);
        rows.put("successful_shift", result.position().x() + "," + result.position().y() + "|used=true");
        rows.put("second_shift_rejected", rejected(() -> ShiftApplication.apply("actor", result.position(),
                new GridCoord(3, 1), Set.of(new GridCoord(3, 1)), budget)));
        rows.put("blocked_rejected", rejected(() -> ShiftApplication.apply("actor", new GridCoord(1, 1),
                new GridCoord(2, 1), Set.of(), new ActionBudget())));
        rows.put("too_far_rejected", rejected(() -> ShiftApplication.apply("actor", new GridCoord(1, 1),
                new GridCoord(5, 5), Set.of(), new ActionBudget())));
        return rows;
    }

    private static String rejected(Runnable action) {
        try {
            action.run();
            return "false";
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return "true";
        }
    }
}
