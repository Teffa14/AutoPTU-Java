package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactionUsageStateTest {
    @Test
    void usageIsIsolatedByCombatantReactionAndRound() {
        ReactionUsageState state = new ReactionUsageState();

        state.recordUseFromRuntime("alpha", "Attack_Of_Opportunity", 3);
        state.recordUseFromRuntime("alpha", "attack_of_opportunity", 3);
        state.recordUseFromRuntime("alpha", "other_reaction", 3);
        state.recordUseFromRuntime("beta", "attack_of_opportunity", 3);

        assertEquals(2, state.usesThisRound("alpha", "ATTACK_OF_OPPORTUNITY", 3));
        assertEquals(1, state.usesThisRound("alpha", "other_reaction", 3));
        assertEquals(1, state.usesThisRound("beta", "attack_of_opportunity", 3));
        assertEquals(0, state.usesThisRound("alpha", "attack_of_opportunity", 4));
    }

    @Test
    void lifecyclePruningDropsOnlyPastRounds() {
        ReactionUsageState state = new ReactionUsageState();
        state.recordUseFromRuntime("alpha", "attack_of_opportunity", 2);
        state.recordUseFromRuntime("alpha", "attack_of_opportunity", 3);
        state.recordUseFromRuntime("beta", "other_reaction", 4);

        state.pruneForRoundFromLifecycle(3);

        assertEquals(0, state.usesThisRound("alpha", "attack_of_opportunity", 2));
        assertEquals(1, state.usesThisRound("alpha", "attack_of_opportunity", 3));
        assertEquals(1, state.usesThisRound("beta", "other_reaction", 4));
    }

    @Test
    void snapshotContainsOnlyRequestedRound() {
        ReactionUsageState state = new ReactionUsageState();
        state.recordUseFromRuntime("alpha", "attack_of_opportunity", 5);
        state.recordUseFromRuntime("alpha", "attack_of_opportunity", 5);
        state.recordUseFromRuntime("beta", "riposte", 4);

        assertEquals(Map.of("alpha\tattack_of_opportunity", 2), state.snapshotForRound(5));
    }

    @Test
    void invalidKeysAndRoundsAreRejected() {
        ReactionUsageState state = new ReactionUsageState();
        assertThrows(IllegalArgumentException.class, () -> state.usesThisRound("", "reaction", 1));
        assertThrows(IllegalArgumentException.class, () -> state.usesThisRound("alpha", "", 1));
        assertThrows(IllegalArgumentException.class, () -> state.usesThisRound("alpha", "reaction", -1));
        assertThrows(IllegalArgumentException.class, () -> state.recordUseFromRuntime("alpha", "reaction", -1));
        assertThrows(IllegalArgumentException.class, () -> state.pruneForRoundFromLifecycle(-1));
    }
}
