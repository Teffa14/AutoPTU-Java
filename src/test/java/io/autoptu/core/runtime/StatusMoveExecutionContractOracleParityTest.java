package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusMoveExecutionContractOracleParityTest {
    @Test
    void statusMovesUseAccuracyButSkipOrdinaryDamageArithmetic() throws IOException {
        Path fixture = Path.of(System.getProperty(
                "autoptu.status.move.execution.oracle",
                "build/oracle/status-move-execution.tsv"
        ));
        Assumptions.assumeTrue(Files.exists(fixture));

        String line = Files.readAllLines(fixture).stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow();
        String[] parts = line.split("\\t", -1);
        assertEquals("STATUS_MOVE_EXECUTION", parts[0]);

        StatusMoveExecutionPolicy policy = StatusMoveExecutionPolicy.pythonOracleContract();
        assertEquals(asBool(parts[1]), policy.statusBranchPresent());
        assertEquals(asBool(parts[2]), policy.hitComesFromAccuracyResult());
        assertEquals(asBool(parts[3]), policy.critIsAlwaysFalse());
        assertEquals(asBool(parts[4]), policy.damageIsAlwaysZero());
        assertEquals(asBool(parts[5]), policy.damageRollIsAlwaysZero());
    }

    private static boolean asBool(String raw) {
        return "1".equals(raw);
    }
}
