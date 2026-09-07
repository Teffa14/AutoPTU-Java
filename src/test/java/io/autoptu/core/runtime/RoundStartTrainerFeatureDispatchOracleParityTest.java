package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RoundStartTrainerFeatureDispatchOracleParityTest {
    @Test
    void typedContractMatchesPinnedPythonInvocationAndOrdering() throws IOException {
        Path fixture = Path.of("build/oracle/round-start-trainer-feature-dispatch.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));

        int round = Integer.parseInt(expected.get("ROUND"));
        RoundStartTrainerFeatureDispatchContract actual = RoundStartTrainerFeatureDispatchContract.forRound(round);

        assertEquals(expected.get("TRIGGER"), actual.trigger());
        assertEquals(Map.of("round", round), actual.payload());
        assertFalse(actual.actorArgumentPresent());
        assertFalse(actual.targetArgumentPresent());
        assertEquals("absent", expected.get("ACTOR_ARGUMENT"));
        assertEquals("absent", expected.get("TARGET_ARGUMENT"));
        assertEquals(
                "round_start_event,trainer_feature_dispatch,air_lock",
                expected.get("TIMELINE"),
                "Python dispatch must remain after the semantic round_start event and before round-start abilities"
        );
    }

    @Test
    void roundMustBePositive() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> RoundStartTrainerFeatureDispatchContract.forRound(0)
        );
    }

    private static Map<String, String> parse(List<String> lines) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            if (parts.length != 2) throw new IllegalArgumentException("invalid fixture row: " + line);
            result.put(parts[0], parts[1]);
        }
        return Map.copyOf(result);
    }
}
