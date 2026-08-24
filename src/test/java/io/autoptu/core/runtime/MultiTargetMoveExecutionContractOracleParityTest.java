package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiTargetMoveExecutionContractOracleParityTest {
    @Test
    void ordinaryAreaMovesResolveTargetsIndividuallyButSpendDeclarationResourcesOnce() throws IOException {
        Path fixture = Path.of(System.getProperty(
                "autoptu.multi.target.move.execution.oracle",
                "build/oracle/multi-target-move-execution.tsv"
        ));
        Assumptions.assumeTrue(Files.exists(fixture));

        String line = Files.readAllLines(fixture).stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow();
        String[] parts = line.split("\\t", -1);
        assertEquals("MULTI_TARGET_MOVE_EXECUTION", parts[0]);

        MultiTargetMoveExecutionPolicy policy = MultiTargetMoveExecutionPolicy.pythonOracleContract();
        assertEquals(asBool(parts[1]), policy.resolvesEachTargetInsideTargetLoop());
        assertEquals(asBool(parts[2]), policy.targetLoopMarksAction());
        assertEquals(asBool(parts[3]), policy.targetLoopRecordsMoveFrequency());
        assertEquals(asBool(parts[4]), policy.targetLoopRecordsMoveUsed());
        assertEquals(asBool(parts[5]), policy.ordinaryActionMarksAction());
        assertEquals(asBool(parts[6]), policy.ordinaryMoveChecksFrequency());
        assertEquals(asBool(parts[7]), policy.ordinaryMoveRecordsFrequency());
        assertEquals(asBool(parts[8]), policy.ordinaryMoveRecordsMoveUsed());
    }

    private static boolean asBool(String raw) {
        return "1".equals(raw);
    }
}
