package io.autoptu.core.hook;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttackOfOpportunityOracleParityTest {
    @Test
    void matchesPinnedPythonTriggerFixtureWhenProvided() throws Exception {
        String fixturePath = System.getenv("AUTOPTU_ATTACK_OF_OPPORTUNITY_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        List<String> rows = Files.readAllLines(Path.of(fixturePath));
        for (String row : rows.subList(1, rows.size())) {
            String[] parts = row.split("\\t", -1);
            AttackOfOpportunityTriggerDetector.Kind kind = AttackOfOpportunityTriggerDetector.Kind.valueOf(parts[0]);
            Optional<ActionWindowTrigger> actual = AttackOfOpportunityTriggerDetector.detect(
                    new AttackOfOpportunityTriggerDetector.Input(
                            true,
                            kind,
                            parts[1],
                            Boolean.parseBoolean(parts[2]),
                            Boolean.parseBoolean(parts[3]),
                            Boolean.parseBoolean(parts[4]),
                            Boolean.parseBoolean(parts[5])
                    )
            );
            assertEquals(Optional.of(ActionWindowTrigger.valueOf(parts[6])), actual, row);
        }
    }
}
