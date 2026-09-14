package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactionTimingContractOracleParityTest {
    @Test
    void matchesPinnedPythonTimingContractWhenProvided() throws Exception {
        String fixturePath = System.getenv("AUTOPTU_REACTION_TIMING_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        List<String> rows = Files.readAllLines(Path.of(fixturePath));
        for (String row : rows.subList(1, rows.size())) {
            String[] parts = row.split("\\t", -1);
            ReactionTimingContract contract = ReactionTimingContract.fromTrait(parts[1]);

            assertEquals(ReactionTimingContract.Timing.valueOf(parts[2]), contract.timing(), parts[0]);
            assertEquals(Integer.parseInt(parts[3]), contract.rank().orElseThrow(), parts[0]);
            assertEquals(Boolean.parseBoolean(parts[4]), contract.spendsOrdinaryActionBudget(), parts[0]);
        }
    }
}
