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
    void delayedHitsEnterTargetResolutionBeforeMoveActionResolution() throws IOException {
        String fixturePath = System.getProperty(
                "autoptu.delayed.hit.execution.oracle",
                "build/oracle/delayed-hit-execution.tsv"
        );
        Path fixture = Path.of(fixturePath);
        Assumptions.assumeTrue(Files.exists(fixture));

        String line = Files.readAllLines(fixture).stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow();
        String[] parts = line.split("\\t", -1);

        assertEquals("TARGET_RESOLUTION", parts[0]);
        assertTrue(asBool(parts[1]), "resolve_delayed_hits must call resolve_move_targets");
        assertFalse(asBool(parts[2]), "resolve_delayed_hits must not call resolve_move_action directly");
        assertTrue(asBool(parts[3]), "target_id must be forwarded");
        assertTrue(asBool(parts[4]), "target_position must be forwarded");
        assertTrue(asBool(parts[5]), "resolve_move_targets must re-enter resolve_move_action");
        assertFalse(asBool(parts[6]), "target_position must not rewrite the move into Tile targeting");
        assertTrue(asBool(parts[7]), "a resolved target id must use defender.current position before stored target_position");
        assertTrue(asBool(parts[8]), "area geometry must be recomputed with targeting.affected_tiles");
        assertTrue(asBool(parts[9]), "area target selection must use combatant footprint overlap");
        assertTrue(asBool(parts[10]), "area propagation must re-evaluate line of sight");
        assertTrue(asBool(parts[11]), "target_id must retain priority when collecting area targets");

        DelayedHitExecutionPolicy policy = DelayedHitExecutionPolicy.targetResolution();
        assertEquals(DelayedHitExecutionPolicy.EntryPoint.TARGET_RESOLUTION, policy.entryPoint());
        assertEquals(asBool(parts[3]), policy.forwardsTargetId());
        assertEquals(asBool(parts[4]), policy.forwardsTargetPosition());
        assertEquals(asBool(parts[5]), policy.targetResolutionReentersMoveAction());
        assertEquals(asBool(parts[6]), policy.targetPositionForcesTile());
    }

    private static boolean asBool(String raw) {
        return "1".equals(raw);
    }
}
