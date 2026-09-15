package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEventOccurrence;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.hook.ReactionEligibilityPolicy;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeReactionWindowCommitterTest {
    private static final String REACTION = "attack_of_opportunity";

    @Test
    void commitsUsageOnceAndRejectsReplayAfterRevalidation() {
        BattleRuntimeState state = battle();
        RuntimeReactionUsageTracker usage = new RuntimeReactionUsageTracker(state);
        RuntimeReactionWindowResolver.Candidate candidate = candidate(state, 1);
        RuntimeReactionWindowCommitter committer = new RuntimeReactionWindowCommitter(state, usage);

        RuntimeReactionWindowCommitter.CommitResult first = committer.commit(
                candidate,
                ReactionEligibilityPolicy.attackOfOpportunity()
        );
        RuntimeReactionWindowCommitter.CommitResult replay = committer.commit(
                candidate,
                ReactionEligibilityPolicy.attackOfOpportunity()
        );

        assertTrue(first.committed());
        assertEquals(RuntimeReactionWindowCommitter.CommitReason.COMMITTED, first.reason());
        assertFalse(replay.committed());
        assertEquals(RuntimeReactionWindowCommitter.CommitReason.ROUND_USE_EXHAUSTED, replay.reason());
        assertEquals(1, usage.usesThisRound("reactor", REACTION));
    }

    @Test
    void rejectsWindowFromEarlierRoundWithoutConsumingCurrentUsage() {
        BattleRuntimeState state = battle();
        RuntimeReactionUsageTracker usage = new RuntimeReactionUsageTracker(state);
        RuntimeReactionWindowResolver.Candidate candidate = candidate(state, 1);
        state.advanceRound();

        RuntimeReactionWindowCommitter.CommitResult result = new RuntimeReactionWindowCommitter(state, usage)
                .commit(candidate, ReactionEligibilityPolicy.attackOfOpportunity());

        assertFalse(result.committed());
        assertEquals(RuntimeReactionWindowCommitter.CommitReason.STALE_ROUND, result.reason());
        assertEquals(0, usage.usesThisRound("reactor", REACTION));
    }

    private static RuntimeReactionWindowResolver.Candidate candidate(BattleRuntimeState state, long sequence) {
        return new RuntimeReactionWindowResolver(state).discoverShiftWindows(
                REACTION,
                new BattleEventOccurrence(
                        sequence,
                        new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(2, 0))
                ),
                ReactionEligibilityPolicy.attackOfOpportunity()
        ).eligible().getFirst();
    }

    private static BattleRuntimeState battle() {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(
                        combatant("reactor", new GridCoord(0, 0)),
                        combatant("actor", new GridCoord(2, 0))
                ),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "reactor", CombatantAffiliationState.active("blue"),
                        "actor", CombatantAffiliationState.active("red")
                ),
                Map.of(
                        "reactor", List.of(move("Attack of Opportunity")),
                        "actor", List.of(move("Tackle"))
                )
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 6),
                20,
                20,
                new ActionBudget()
        );
    }

    private static MoveOption move(String id) {
        return MoveOption.standard(id, new MoveSpec("Self", "Self", 0, 0, null, null, "Self"));
    }
}
