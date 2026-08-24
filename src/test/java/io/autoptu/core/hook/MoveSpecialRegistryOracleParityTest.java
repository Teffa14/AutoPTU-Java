package io.autoptu.core.hook;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoveSpecialRegistryOracleParityTest {
    @Test
    void registryDispatchContractMatchesPinnedPython() throws IOException {
        Path fixture = Path.of(System.getProperty(
                "autoptu.move.special.registry.oracle",
                "build/oracle/move-special-registry.tsv"));
        Assumptions.assumeTrue(Files.exists(fixture));
        String[] parts = Files.readAllLines(fixture).stream()
                .filter(line -> line != null && !line.isBlank())
                .findFirst().orElseThrow().split("\\t", -1);
        assertEquals("MOVE_SPECIAL_REGISTRY", parts[0]);
        MoveSpecialDispatchPolicy policy = MoveSpecialDispatchPolicy.pythonOracleContract();
        assertEquals(asBool(parts[1]), policy.unknownPhaseDefaultsToPostDamage());
        assertEquals(asBool(parts[2]), policy.postDamageRunsSpecificBeforeGlobal());
        assertEquals(asBool(parts[3]), policy.otherPhasesRunGlobalBeforeSpecific());
        assertEquals(asBool(parts[4]), policy.moveNamesNormalizeTrimLower());
        assertEquals(asBool(parts[5]), policy.shieldDustSkipsNonStatusPostDamage());
        assertEquals(asBool(parts[6]), policy.shieldDustAllowsStatusPostDamage());
    }

    private static boolean asBool(String raw) {
        return "1".equals(raw);
    }
}