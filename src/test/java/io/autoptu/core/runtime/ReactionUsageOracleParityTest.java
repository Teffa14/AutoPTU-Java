package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactionUsageOracleParityTest {
    @Test
    void matchesPinnedPythonPerRoundUsageContractWhenProvided() throws Exception {
        String fixturePath = System.getenv("AUTOPTU_REACTION_USAGE_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        ReactionUsageState state = new ReactionUsageState();
        List<String> rows = Files.readAllLines(Path.of(fixturePath));
        for (String row : rows.subList(1, rows.size())) {
            String[] parts = row.split("\\t", -1);
            String operation = parts[0];
            String combatant = parts[1];
            String reactionKey = parts[2];
            int round = Integer.parseInt(parts[3]);
            int expectedUses = Integer.parseInt(parts[4]);

            switch (operation) {
                case "query" -> assertEquals(expectedUses, state.usesThisRound(combatant, reactionKey, round), row);
                case "record" -> {
                    state.recordUseFromRuntime(combatant, reactionKey, round);
                    assertEquals(expectedUses, state.usesThisRound(combatant, reactionKey, round), row);
                }
                case "prune" -> state.pruneForRoundFromLifecycle(round);
                default -> throw new IllegalArgumentException("unknown fixture operation: " + operation);
            }
        }
    }
}
