package io.autoptu.core.oracle;

import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.PhaseSequence;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TurnFlowOracleFixtureTest {
    @Test
    void javaEnumsAndPhaseOrderMatchPinnedPythonSource() throws IOException {
        String fixturePath = System.getProperty("autoptu.turnflow.oracle", "").strip();
        Assumptions.assumeFalse(fixturePath.isEmpty(), "Python turn-flow fixture path not configured");

        Map<String, String> expected = readFixtures(Path.of(fixturePath));
        Map<String, String> actual = javaResults();
        assertEquals(expected, actual);
    }

    private static Map<String, String> javaResults() {
        Map<String, String> result = new LinkedHashMap<>();
        for (ActionType type : ActionType.values()) {
            result.put("action_" + type.name(), type.value());
        }
        for (TurnPhase phase : TurnPhase.values()) {
            result.put("phase_" + phase.name(), phase.value());
        }
        result.put(
                "phase_sequence",
                PhaseSequence.ORDER.stream().map(Enum::name).collect(Collectors.joining(","))
        );
        return result;
    }

    private static Map<String, String> readFixtures(Path path) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\t", 2);
            result.put(parts[0], parts[1]);
        }
        return result;
    }
}
