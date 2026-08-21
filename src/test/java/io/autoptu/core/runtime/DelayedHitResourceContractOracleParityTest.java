package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedHitResourceContractOracleParityTest {
    @Test
    void maturityDoesNotDoubleSpendNormalMoveResources() throws IOException {
        Path fixture = Path.of("build/oracle/delayed-hit-resource.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));

        String line = Files.readAllLines(fixture).stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow();
        String[] parts = line.split("\\t", -1);

        assertEquals("DELAYED_RESOURCE_POLICY", parts[0]);
        assertTrue(asBool(parts[1]), "matured delayed hits must enter target resolution");
        assertTrue(asBool(parts[2]), "normal MoveAction must validate frequency");
        assertTrue(asBool(parts[3]), "normal MoveAction must record frequency usage");
        assertTrue(asBool(parts[4]), "normal MoveAction must record ordinary move use");
        assertFalse(asBool(parts[5]), "target resolution must not consume frequency again");
        assertFalse(asBool(parts[6]), "target resolution must not record ordinary move use again");
        assertFalse(asBool(parts[7]), "target resolution must not mark action economy");
        assertTrue(asBool(parts[8]), "target resolution must still execute attack resolution");

        DelayedHitResourcePolicy policy = DelayedHitResourcePolicy.pythonOracle();
        assertEquals(asBool(parts[1]), policy.entersTargetResolution());
        assertFalse(policy.spendsActionAtMaturity());
        assertEquals(asBool(parts[5]), policy.consumesFrequencyAtMaturity());
        assertEquals(asBool(parts[6]), policy.recordsNormalMoveUseAtMaturity());
        assertEquals(asBool(parts[8]), policy.resolvesAttackAtMaturity());
    }

    private static boolean asBool(String raw) {
        return "1".equals(raw);
    }
}
