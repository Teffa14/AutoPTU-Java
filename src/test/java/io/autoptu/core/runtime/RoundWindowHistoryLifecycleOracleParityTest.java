package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundWindowHistoryLifecycleOracleParityTest {
    @Test
    @SuppressWarnings("deprecation")
    void authoritativeRolloverPrunesCanonicalHistoriesAfterInitiativeRebuildLikePython() throws IOException {
        Path fixture = Path.of("build/oracle/round-window-history.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));

        for (String line : Files.readAllLines(fixture)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            assertEquals("ROUND_WINDOW_HISTORY", parts[0]);
            String caseName = parts[1];
            int currentRound = Integer.parseInt(parts[2]);

            RuntimeCombatantState actor = new RuntimeCombatantState(
                    "actor",
                    MovementProfile.walking(new GridCoord(1, 1), 1),
                    20,
                    20,
                    new ActionBudget()
            );
            BattleRuntimeState state = new BattleRuntimeState(
                    new MovementGrid(4, 4, Set.of(), Map.of()),
                    List.of(actor)
            );
            RoundWindowHistoryState histories = state.roundWindowHistories();
            List<Integer> echoedBefore = parseRounds(parts[3]);
            List<Integer> boltBefore = parseRounds(parts[5]);
            List<Integer> flareBefore = parseRounds(parts[7]);
            histories.replaceRoundsFromRuntime(RoundWindowHistoryState.ECHOED_VOICE_ROUNDS, echoedBefore);
            histories.replaceRoundsFromRuntime(RoundWindowHistoryState.FUSION_BOLT_ROUNDS, boltBefore);
            histories.replaceRoundsFromRuntime(RoundWindowHistoryState.FUSION_FLARE_ROUNDS, flareBefore);

            BattleRoundController rounds = new BattleRoundController(state, currentRound - 1);
            InitiativeTurnAdvanceResult result = rounds.advanceInitiativeTurnWithRollover((runtime, round) -> {
                assertEquals(currentRound, round, caseName + " rebuilt round");
                assertEquals(echoedBefore, runtime.roundWindowHistories().rounds(RoundWindowHistoryState.ECHOED_VOICE_ROUNDS), caseName + " echoed before post-initiative hook");
                assertEquals(boltBefore, runtime.roundWindowHistories().rounds(RoundWindowHistoryState.FUSION_BOLT_ROUNDS), caseName + " bolt before post-initiative hook");
                assertEquals(flareBefore, runtime.roundWindowHistories().rounds(RoundWindowHistoryState.FUSION_FLARE_ROUNDS), caseName + " flare before post-initiative hook");
                return List.of("actor");
            });

            assertTrue(result.hasActor(), caseName + " has next actor");
            assertEquals(parseRounds(parts[4]), histories.rounds(RoundWindowHistoryState.ECHOED_VOICE_ROUNDS), caseName + " echoed_voice_rounds");
            assertEquals(parseRounds(parts[6]), histories.rounds(RoundWindowHistoryState.FUSION_BOLT_ROUNDS), caseName + " fusion_bolt_rounds");
            assertEquals(parseRounds(parts[8]), histories.rounds(RoundWindowHistoryState.FUSION_FLARE_ROUNDS), caseName + " fusion_flare_rounds");
        }
    }

    private static List<Integer> parseRounds(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(Integer::parseInt).toList();
    }
}
