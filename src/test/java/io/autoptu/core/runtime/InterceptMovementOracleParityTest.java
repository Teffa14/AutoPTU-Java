package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterceptMovementOracleParityTest {
    @Test
    void movementContractMatchesPinnedPython() throws IOException {
        String fixturePath = System.getProperty("autoptu.intercept.movement.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(fixturePath));

        assertEquals(1, fixture.get("commits_interceptor_to_intercept_pos"));
        assertEquals(1, fixture.get("failed_check_has_early_return"));
        assertEquals(1, fixture.get("intercept_path_does_not_consume_shift_bucket"));
        assertEquals(1, fixture.get("melee_branch_uses_forced_movement"));
    }

    private static Map<String, Integer> readFixture(Path path) throws IOException {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            if (parts.length == 2) values.put(parts[0], Integer.parseInt(parts[1]));
        }
        return values;
    }
}
