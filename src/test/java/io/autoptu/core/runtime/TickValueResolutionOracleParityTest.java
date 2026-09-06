package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TickValueResolutionOracleParityTest {
    @Test
    void matchesPinnedPythonTickValue() throws IOException {
        String oracle = System.getenv("AUTOPTU_TICK_VALUE_ORACLE");
        assumeTrue(oracle != null && !oracle.isBlank(), "Tick value fixture not configured");

        List<String> lines = Files.readAllLines(Path.of(oracle));
        assertEquals("hp_stat\tmax_hp\ttick_value", lines.getFirst());
        assertEquals(8, lines.size(), "Expected header plus seven frozen oracle cases");

        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\\t", -1);
            int maxHp = Integer.parseInt(fields[1]);
            int expectedTick = Integer.parseInt(fields[2]);
            assertEquals(expectedTick, TickValueResolution.resolve(maxHp), "max_hp=" + maxHp);
        }
    }
}
