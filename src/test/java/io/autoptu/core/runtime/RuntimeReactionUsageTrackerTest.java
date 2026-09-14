package io.autoptu.core.runtime;

import io.autoptu.core.model.MovementGrid;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeReactionUsageTrackerTest {
    @Test
    void canonicalRoundRolloverMakesPriorReactionUseIneligibleWithoutASecondClock() {
        BattleRuntimeState battleState = new BattleRuntimeState(
                new MovementGrid(1, 1, Set.of(), Map.of()),
                List.of()
        );
        BattleRoundController rounds = new BattleRoundController(battleState, 1);
        RuntimeReactionUsageTracker tracker = new RuntimeReactionUsageTracker(battleState);

        tracker.recordUseFromRuntime("alpha", "attack_of_opportunity");
        assertEquals(1, tracker.usesThisRound("alpha", "attack_of_opportunity"));
        assertEquals(Map.of("alpha\tattack_of_opportunity", 1), tracker.snapshotForCurrentRound());

        rounds.startRound();

        assertEquals(2, battleState.currentRound());
        assertEquals(0, tracker.usesThisRound("alpha", "attack_of_opportunity"));
        assertEquals(Map.of(), tracker.snapshotForCurrentRound());
    }

    @Test
    void lifecyclePruningDropsPastUsageAfterCanonicalRoundAdvances() {
        BattleRuntimeState battleState = new BattleRuntimeState(
                new MovementGrid(1, 1, Set.of(), Map.of()),
                List.of()
        );
        BattleRoundController rounds = new BattleRoundController(battleState, 4);
        ReactionUsageState rawUsage = new ReactionUsageState();
        RuntimeReactionUsageTracker tracker = new RuntimeReactionUsageTracker(battleState, rawUsage);

        tracker.recordUseFromRuntime("alpha", "attack_of_opportunity");
        rounds.startRound();
        tracker.pruneForCurrentRoundFromLifecycle();

        assertEquals(0, rawUsage.usesThisRound("alpha", "attack_of_opportunity", 4));
        assertEquals(0, tracker.usesThisRound("alpha", "attack_of_opportunity"));
    }
}
