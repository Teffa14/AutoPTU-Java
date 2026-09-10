package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CombatantSwitchPostEntryPlanOracleParityTest {
    @Test
    void semanticFamilyOrderMatchesPinnedPythonApplySwitch() throws IOException {
        Path fixture = Path.of("build/oracle/switch-post-entry-calls.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));

        String expected = Files.readAllLines(fixture).stream()
                .filter(line -> line.startsWith("ORDER\t"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing ORDER in switch post-entry fixture"))
                .substring("ORDER\t".length());

        String actual = CombatantSwitchPostEntryPlan.pinnedContract().stages().stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));

        assertEquals(expected, actual);
    }
}
