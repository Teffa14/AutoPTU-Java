package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundDamageHistoryStateTest {
    @Test
    void rotationSnapshotsCurrentRoundAndClearsMutableBuckets() {
        RoundDamageHistoryState history = new RoundDamageHistoryState();
        history.recordDamageThisRound("attacker");
        history.recordDamageTakenFrom("target", "attacker");
        history.recordDamageReceivedThisRound("target");

        history.rotateForNewRound();

        assertEquals(Set.of("attacker"), history.damageLastRound());
        assertEquals(Set.of("attacker"), history.damageTakenFromLastRound().get("target"));
        assertTrue(history.damageThisRound().isEmpty());
        assertTrue(history.damageTakenFromThisRound().isEmpty());
        assertTrue(history.damageReceivedThisRound().isEmpty());
    }

    @Test
    void laterRoundMutationDoesNotAlterPreviousRoundSnapshots() {
        RoundDamageHistoryState history = new RoundDamageHistoryState();
        history.recordDamageThisRound("first");
        history.recordDamageTakenFrom("target", "first");
        history.rotateForNewRound();

        history.recordDamageThisRound("second");
        history.recordDamageTakenFrom("target", "second");

        assertEquals(Set.of("first"), history.damageLastRound());
        assertEquals(Set.of("first"), history.damageTakenFromLastRound().get("target"));
    }
}
