package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoundWindowHistoryPruningTest {
    @Test
    void retainsInclusiveWindowAndPreservesOrderAndDuplicates() {
        assertEquals(
                List.of(3, 4, 3, 5),
                RoundWindowHistoryPruning.retain(List.of(1, 3, 4, 3, 5), 5, 2)
        );
    }

    @Test
    void supportsEchoedVoiceTwoRoundLookback() {
        assertEquals(
                List.of(3, 4, 5),
                RoundWindowHistoryPruning.retain(List.of(1, 2, 3, 4, 5), 5, 2)
        );
    }

    @Test
    void supportsFusionOneRoundLookback() {
        assertEquals(
                List.of(4, 5),
                RoundWindowHistoryPruning.retain(List.of(1, 2, 3, 4, 5), 5, 1)
        );
    }

    @Test
    void permitsEarlyRoundsWithoutArtificialFloor() {
        assertEquals(
                List.of(0, 1),
                RoundWindowHistoryPruning.retain(List.of(0, 1), 1, 2)
        );
    }

    @Test
    void rejectsInvalidContractInputs() {
        assertThrows(NullPointerException.class, () -> RoundWindowHistoryPruning.retain(null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> RoundWindowHistoryPruning.retain(List.of(1), -1, 1));
        assertThrows(IllegalArgumentException.class, () -> RoundWindowHistoryPruning.retain(List.of(1), 1, -1));
    }
}
