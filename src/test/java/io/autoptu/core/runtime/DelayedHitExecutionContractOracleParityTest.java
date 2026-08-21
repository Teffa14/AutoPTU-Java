package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedHitExecutionContractOracleParityTest {
    @Test
    void delayedHitsBypassOrdinaryMoveActionEntrypoint() throws IOException {
        String fixturePath = System.getProperty("autoptu.delayed.hit.execution.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());

        String line = Files.readAllLines(Path.of(fixturePath)).stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow();
        String[] parts = line.split("\\t", -1);

        assertEquals("TARGET_RESOLUTION", parts[0]);
        assertTrue(asBool(parts[1]), "resolve_delayed_hits must call resolve_move_targets");
        assertFalse(asBool(parts[2]), "resolve_delayed_hits must bypass resolve_move_action");
        assertTrue(asBool(parts[3]), "target_id must be forwarded");
        assertTrue(asBool(parts[4]), "target_position must be forwarded");
        assertFalse(asBool(parts[5]), "resolve_move_targets must not re-enter resolve_move_action");

        DelayedHitExecutionPolicy policy = DelayedHitExecutionPolicy.targetResolution();
        assertEquals(DelayedHitExecutionPolicy.EntryPoint.TARGET_RESOLUTION, policy.entryPoint());
        assertEquals(asBool(parts[3]), policy.forwardsTargetId());
        assertEquals(asBool(parts[4]), policy.forwardsTargetPosition());
    }

    private static boolean asBool(String raw) {
        return "1".equals(raw);
    }
}
