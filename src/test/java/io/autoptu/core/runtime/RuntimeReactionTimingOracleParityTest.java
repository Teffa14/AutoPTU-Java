package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeReactionTimingOracleParityTest {
    @Test
    void matchesPinnedPythonTimingContractWhenProvided() throws Exception {
        String fixturePath = System.getenv("AUTOPTU_REACTION_TIMING_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        RuntimeReactionTimingRegistry registry = RuntimeReactionTimingRegistry.builtin();
        List<String> rows = Files.readAllLines(Path.of(fixturePath));
        for (String row : rows.subList(1, rows.size())) {
            String[] parts = row.split("\\t", -1);
            String reactionKey = parts[0];
            RuntimeReactionTimingRegistry.Timing expected = RuntimeReactionTimingRegistry.Timing.valueOf(parts[1]);

            assertEquals(expected, registry.resolve(reactionKey).orElseThrow(), reactionKey);
        }
    }
}
