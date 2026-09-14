package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
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

class RuntimeReactionCommitterTest {
    private static RuntimeCombatantState alpha() {
        return new RuntimeCombatantState(
                "alpha",
                MovementProfile.walking(new GridCoord(0, 0), 5),
                20,
                20,
                new ActionBudget()
        );
    }

    private static MoveOption move(String id) {
        return MoveOption.standard(id, new MoveSpec("Self", "Self", 0, 0, null, null, "Self"));
    }

    private static BattleRuntimeState state(Set<String> statuses, boolean materializeMoves, String... moveIds) {
        List<MoveOption> moves = java.util.Arrays.stream(moveIds).map(RuntimeReactionCommitterTest::move).toList();
        if (!materializeMoves) {
            return new BattleRuntimeState(
                    new MovementGrid(2, 1, Set.of(), Map.of()),
                    List.of(alpha()),
                    Map.of("alpha", statuses)
            );
        }
        return new BattleRuntimeState(
                new MovementGrid(2, 1, Set.of(), Map.of()),
                List.of(alpha()),
                Map.of("alpha", statuses),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("alpha", moves)
        );
    }

    @Test
    void eligibleReactionCommitsExactlyOneUseAndSecondCommitIsBlocked() {
        BattleRuntimeState state = state(Set.of(), true, "Attack of Opportunity");
        RuntimeReactionCommitter committer = new RuntimeReactionCommitter(state);
        ReactionEligibilityPolicy policy = ReactionEligibilityPolicy.attackOfOpportunity();

        RuntimeReactionCommitter.Result first = committer.commit("alpha", "attack_of_opportunity", policy);
        RuntimeReactionCommitter.Result second = committer.commit("alpha", "attack_of_opportunity", policy);

        assertTrue(first.committed());
        assertEquals(1, first.usesThisRound());
        assertEquals(RuntimeReactionCommitter.Status.BLOCKED, second.status());
        assertEquals(ReactionEligibilityPolicy.Reason.ROUND_USE_EXHAUSTED, second.eligibility().orElseThrow().reason());
        assertEquals(1, second.usesThisRound());
        assertEquals(1, committer.usesThisRound("alpha", "attack_of_opportunity"));
    }

    @Test
    void blockedStatusDoesNotConsumeUsage() {
        BattleRuntimeState state = state(Set.of("Sleeping"), true, "Attack of Opportunity");
        RuntimeReactionCommitter committer = new RuntimeReactionCommitter(state);

        RuntimeReactionCommitter.Result result = committer.commit(
                "alpha",
                "attack_of_opportunity",
                ReactionEligibilityPolicy.attackOfOpportunity()
        );

        assertFalse(result.committed());
        assertEquals(RuntimeReactionCommitter.Status.BLOCKED, result.status());
        assertEquals(ReactionEligibilityPolicy.Reason.BLOCKED_BY_STATUS, result.eligibility().orElseThrow().reason());
        assertEquals(0, result.usesThisRound());
    }

    @Test
    void unknownOwnershipDoesNotConsumeUsage() {
        BattleRuntimeState state = state(Set.of(), false);
        RuntimeReactionCommitter committer = new RuntimeReactionCommitter(state);

        RuntimeReactionCommitter.Result result = committer.commit(
                "alpha",
                "attack_of_opportunity",
                ReactionEligibilityPolicy.attackOfOpportunity()
        );

        assertEquals(RuntimeReactionCommitter.Status.OWNERSHIP_UNKNOWN, result.status());
        assertTrue(result.eligibility().isEmpty());
        assertEquals(0, result.usesThisRound());
    }

    @Test
    void lifecycleRolloverAllowsNewCommitWithoutParallelLedger() {
        BattleRuntimeState state = state(Set.of(), true, "Attack of Opportunity");
        RuntimeReactionCommitter committer = new RuntimeReactionCommitter(state);
        BattleRoundController rounds = new BattleRoundController(state, 1);
        ReactionEligibilityPolicy policy = ReactionEligibilityPolicy.attackOfOpportunity();

        assertTrue(committer.commit("alpha", "attack_of_opportunity", policy).committed());
        assertEquals(1, committer.usesThisRound("alpha", "attack_of_opportunity"));

        rounds.startRound();

        assertEquals(0, committer.usesThisRound("alpha", "attack_of_opportunity"));
        assertTrue(committer.commit("alpha", "attack_of_opportunity", policy).committed());
        assertEquals(1, committer.usesThisRound("alpha", "attack_of_opportunity"));
    }
}
