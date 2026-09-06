package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundWindowHistoryStateOracleParityTest {
    @Test
    void declarativeStoreMatchesPinnedPythonStartRoundHistories() throws IOException {
        Path fixture = Path.of("build/oracle/round-window-history.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));

        for (String line : Files.readAllLines(fixture)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            assertEquals("ROUND_WINDOW_HISTORY", parts[0]);
            String caseName = parts[1];
            int currentRound = Integer.parseInt(parts[2]);

            RoundWindowHistoryState state = RoundWindowHistoryState.pythonMoveHistories();
            state.replaceRoundsFromRuntime(RoundWindowHistoryState.ECHOED_VOICE_ROUNDS, parseRounds(parts[3]));
            state.replaceRoundsFromRuntime(RoundWindowHistoryState.FUSION_BOLT_ROUNDS, parseRounds(parts[5]));
            state.replaceRoundsFromRuntime(RoundWindowHistoryState.FUSION_FLARE_ROUNDS, parseRounds(parts[7]));

            state.pruneForRoundFromLifecycle(currentRound);

            assertEquals(parseRounds(parts[4]), state.rounds(RoundWindowHistoryState.ECHOED_VOICE_ROUNDS), caseName + " echoed_voice_rounds");
            assertEquals(parseRounds(parts[6]), state.rounds(RoundWindowHistoryState.FUSION_BOLT_ROUNDS), caseName + " fusion_bolt_rounds");
            assertEquals(parseRounds(parts[8]), state.rounds(RoundWindowHistoryState.FUSION_FLARE_ROUNDS), caseName + " fusion_flare_rounds");
        }
    }

    private static List<Integer> parseRounds(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(Integer::parseInt).toList();
    }
}
