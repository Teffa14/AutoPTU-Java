package io.autoptu.core.hook;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactionEligibilityOracleParityTest {
    @Test
    void matchesPinnedPythonEligibilityFixtureWhenProvided() throws Exception {
        String fixturePath = System.getenv("AUTOPTU_REACTION_ELIGIBILITY_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        ReactionEligibilityPolicy policy = ReactionEligibilityPolicy.attackOfOpportunity();
        List<String> rows = Files.readAllLines(Path.of(fixturePath));
        for (String row : rows.subList(1, rows.size())) {
            String[] parts = row.split("\\t", -1);
            Set<String> statuses = parts[1].isBlank()
                    ? Set.of()
                    : Arrays.stream(parts[1].split(",")).collect(Collectors.toSet());
            ReactionEligibilityPolicy.Eligibility actual = policy.evaluate(
                    new ReactionEligibilityPolicy.Context(
                            Boolean.parseBoolean(parts[0]),
                            statuses,
                            Integer.parseInt(parts[2])
                    )
            );
            assertEquals(ReactionEligibilityPolicy.Reason.valueOf(parts[3]), actual.reason(), row);
        }
    }
}
