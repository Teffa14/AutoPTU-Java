package io.autoptu.core.hook;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AnalyticResolutionOracleParityTest {
    @Test
    void analyticEligibilityMatchesPinnedPythonOracle() throws IOException {
        String oraclePath = System.getProperty("autoptu.analytic.oracle");
        if (oraclePath == null || oraclePath.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertFalse(lines.isEmpty());
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            String[] c = lines.get(index).split("\\t", -1);
            String name = c[0];
            String category = c[1];
            boolean actionsTaken = Integer.parseInt(c[2]) > 0;
            int initiativeIndex = Integer.parseInt(c[3]);
            int defenderIndex = Integer.parseInt(c[4]);
            int expectedBonus = Integer.parseInt(c[5]);
            int expectedEvents = Integer.parseInt(c[6]);

            AnalyticResolution resolution = AnalyticResolution.resolve(
                    !"status".equalsIgnoreCase(category),
                    actionsTaken,
                    initiativeIndex,
                    defenderIndex
            );

            assertEquals(expectedBonus, resolution.damageBonus(), name + " damage bonus");
            assertEquals(expectedEvents > 0, resolution.defenderActed(), name + " acted observation");
            assertEquals(expectedEvents, resolution.damageBonus() == 0 ? 0 : 1, name + " event contract");
        }
    }

    @Test
    void initiativeCursorMustPassDefenderNotMerelyReachIt() {
        assertEquals(0, AnalyticResolution.resolve(true, false, 1, 1).damageBonus());
        assertEquals(5, AnalyticResolution.resolve(true, false, 2, 1).damageBonus());
    }
}
