package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundDamageHistoryStateTest {
    @Test
    void rotationSnapshotsDamagedTargetsAndSourcesAndClearsCurrentBuckets() {
        RoundDamageHistoryState history = new RoundDamageHistoryState();
        history.recordMoveHit("attacker", "target", 7);

        history.rotateForNewRound();

        assertEquals(Set.of("target"), history.damageLastRound());
        assertEquals(Set.of("attacker"), history.damageTakenFromLastRound().get("target"));
        assertTrue(history.damageThisRound().isEmpty());
        assertTrue(history.damageTakenFromThisRound().isEmpty());
        assertTrue(history.damageReceivedThisRound().isEmpty());
    }

    @Test
    void repeatedHitsAccumulateActualDamagePerTarget() {
        RoundDamageHistoryState history = new RoundDamageHistoryState();

        history.recordMoveHit("first", "target", 7);
        history.recordMoveHit("second", "target", 5);
        history.recordMoveHit("first", "other", 0);

        assertEquals(Set.of("target", "other"), history.damageThisRound());
        assertEquals(Set.of("first", "second"), history.damageTakenFromThisRound().get("target"));
        assertEquals(Set.of("first"), history.damageTakenFromThisRound().get("other"));
        assertEquals(Map.of("target", 12, "other", 0), history.damageReceivedThisRound());
    }

    @Test
    void laterRoundMutationDoesNotAlterPreviousRoundSnapshots() {
        RoundDamageHistoryState history = new RoundDamageHistoryState();
        history.recordMoveHit("first", "target", 4);
        history.rotateForNewRound();

        history.recordMoveHit("second", "target", 9);

        assertEquals(Set.of("target"), history.damageLastRound());
        assertEquals(Set.of("first"), history.damageTakenFromLastRound().get("target"));
        assertEquals(Map.of("target", 9), history.damageReceivedThisRound());
    }
}
