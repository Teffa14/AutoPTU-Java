package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundWindowHistoryPruningOracleParityTest {
    @Test
    void roundWindowFamiliesMatchPinnedPythonStartRound() throws IOException {
        Path fixture = Path.of("build/oracle/round-window-history.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));

        List<String> lines = Files.readAllLines(fixture).stream()
                .filter(line -> line != null && !line.isBlank())
                .toList();
        Assumptions.assumeFalse(lines.isEmpty());

        for (String line : lines) {
            String[] parts = line.split("\\t", -1);
            assertEquals("ROUND_WINDOW_HISTORY", parts[0]);
            String caseName = parts[1];
            int currentRound = Integer.parseInt(parts[2]);

            assertEquals(
                    parseRounds(parts[4]),
                    RoundWindowHistoryPruning.retain(parseRounds(parts[3]), currentRound, 2),
                    caseName + " echoed_voice_rounds"
            );
            assertEquals(
                    parseRounds(parts[6]),
                    RoundWindowHistoryPruning.retain(parseRounds(parts[5]), currentRound, 1),
                    caseName + " fusion_bolt_rounds"
            );
            assertEquals(
                    parseRounds(parts[8]),
                    RoundWindowHistoryPruning.retain(parseRounds(parts[7]), currentRound, 1),
                    caseName + " fusion_flare_rounds"
            );
        }
    }

    private static List<Integer> parseRounds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(Integer::parseInt)
                .toList();
    }
}
