package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoundWindowHistoryStateTest {
    @Test
    void builtinsExposePythonMoveHistoryDefinitionsInStableOrder() {
        RoundWindowHistoryState state = RoundWindowHistoryState.pythonMoveHistories();

        assertEquals(List.of(
                new RoundWindowHistoryState.Definition(RoundWindowHistoryState.ECHOED_VOICE_ROUNDS, 2),
                new RoundWindowHistoryState.Definition(RoundWindowHistoryState.FUSION_BOLT_ROUNDS, 1),
                new RoundWindowHistoryState.Definition(RoundWindowHistoryState.FUSION_FLARE_ROUNDS, 1)
        ), state.definitions());
    }

    @Test
    void lifecyclePruningUsesEachDeclarativeRetentionWindow() {
        RoundWindowHistoryState state = RoundWindowHistoryState.pythonMoveHistories();
        state.replaceRoundsFromRuntime(RoundWindowHistoryState.ECHOED_VOICE_ROUNDS, List.of(1, 2, 2, 3, 4, 5));
        state.replaceRoundsFromRuntime(RoundWindowHistoryState.FUSION_BOLT_ROUNDS, List.of(1, 2, 3, 4, 5));
        state.replaceRoundsFromRuntime(RoundWindowHistoryState.FUSION_FLARE_ROUNDS, List.of(1, 2, 3, 4, 4, 5));

        state.pruneForRoundFromLifecycle(5);

        assertEquals(List.of(3, 4, 5), state.rounds(RoundWindowHistoryState.ECHOED_VOICE_ROUNDS));
        assertEquals(List.of(4, 5), state.rounds(RoundWindowHistoryState.FUSION_BOLT_ROUNDS));
        assertEquals(List.of(4, 4, 5), state.rounds(RoundWindowHistoryState.FUSION_FLARE_ROUNDS));
    }

    @Test
    void recordingPreservesInsertionOrderAndDuplicates() {
        RoundWindowHistoryState state = RoundWindowHistoryState.pythonMoveHistories();

        state.recordRoundFromRuntime(RoundWindowHistoryState.ECHOED_VOICE_ROUNDS, 2);
        state.recordRoundFromRuntime(RoundWindowHistoryState.ECHOED_VOICE_ROUNDS, 2);
        state.recordRoundFromRuntime(RoundWindowHistoryState.ECHOED_VOICE_ROUNDS, 3);

        assertEquals(List.of(2, 2, 3), state.rounds(RoundWindowHistoryState.ECHOED_VOICE_ROUNDS));
    }

    @Test
    void duplicateOrUnknownDefinitionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RoundWindowHistoryState(List.of(
                new RoundWindowHistoryState.Definition("same", 1),
                new RoundWindowHistoryState.Definition("same", 2)
        )));

        RoundWindowHistoryState state = RoundWindowHistoryState.pythonMoveHistories();
        assertThrows(IllegalArgumentException.class, () -> state.rounds("missing"));
        assertThrows(IllegalArgumentException.class, () -> state.recordRoundFromRuntime("missing", 1));
    }
}
