package io.autoptu.core.oracle;

import io.autoptu.core.event.StatusSkipEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.StatusSkipResolution;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusSkipOracleFixtureTest {
    @Test
    void baseStatusSkipMatchesPinnedPythonController() throws IOException {
        String fixturePath = System.getProperty("autoptu.status.skip.oracle", "").strip();
        Assumptions.assumeFalse(fixturePath.isEmpty(), "Python status-skip fixture path not configured");

        assertEquals(readFixtures(Path.of(fixturePath)), javaResults());
    }

    private static Map<String, String> javaResults() {
        Map<String, String> result = new LinkedHashMap<>();

        result.put("fresh_flinch", run(new ActionBudget(), "Flinch", "flinched"));

        ActionBudget standardSpent = new ActionBudget();
        standardSpent.markAction(ActionType.STANDARD, "Tackle");
        result.put("standard_already_spent", run(standardSpent, "Confused", "failed_check"));

        ActionBudget bothSpent = new ActionBudget();
        bothSpent.markAction(ActionType.STANDARD, "Tackle");
        bothSpent.markAction(ActionType.SHIFT, "Retreat");
        result.put("both_already_spent", run(bothSpent, "Paralyzed", "failed_check"));

        return result;
    }

    private static String run(ActionBudget budget, String status, String reason) {
        StatusSkipResolution.apply(budget);
        StatusSkipEvent event = new StatusSkipEvent("actor", status, TurnPhase.START, reason);
        String standard = budget.consumedDetail(ActionType.STANDARD).orElse("-");
        String shift = budget.consumedDetail(ActionType.SHIFT).orElse("-");
        return "true|" + standard + "|" + shift + "|" + event.stableKey();
    }

    private static Map<String, String> readFixtures(Path path) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            result.put(parts[0], parts[1]);
        }
        return result;
    }
}
