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

class RuntimeReactionEligibilityResolverTest {
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

    private static BattleRuntimeState state(Set<String> statuses, String... moveIds) {
        List<MoveOption> moves = java.util.Arrays.stream(moveIds).map(RuntimeReactionEligibilityResolverTest::move).toList();
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
    void readsOwnershipAndBlockingStatusesFromAuthoritativeBattleState() {
        BattleRuntimeState state = state(Set.of("Sleeping"), "Attack of Opportunity");
        RuntimeReactionUsageTracker tracker = new RuntimeReactionUsageTracker(state);
        RuntimeReactionEligibilityResolver resolver = new RuntimeReactionEligibilityResolver(state, tracker);

        RuntimeReactionEligibilityResolver.Resolution result = resolver.evaluate(
                "alpha",
                "attack_of_opportunity",
                ReactionEligibilityPolicy.attackOfOpportunity()
        );

        assertEquals(RuntimeReactionOwnershipResolver.Status.OWNED, result.ownership().status());
        assertEquals(
                ReactionEligibilityPolicy.Reason.BLOCKED_BY_STATUS,
                result.eligibility().orElseThrow().reason()
        );
    }

    @Test
    void readsUsageFromCanonicalRoundTrackerAndRolloverMakesPriorUseIrrelevant() {
        BattleRuntimeState state = state(Set.of(), "Attack of Opportunity");
        BattleRoundController rounds = new BattleRoundController(state, 1);
        RuntimeReactionUsageTracker tracker = new RuntimeReactionUsageTracker(state);
        RuntimeReactionEligibilityResolver resolver = new RuntimeReactionEligibilityResolver(state, tracker);
        ReactionEligibilityPolicy policy = ReactionEligibilityPolicy.attackOfOpportunity();

        assertEquals(ReactionEligibilityPolicy.Reason.ELIGIBLE,
                resolver.evaluate("alpha", "attack_of_opportunity", policy).eligibility().orElseThrow().reason());

        tracker.recordUseFromRuntime("alpha", "attack_of_opportunity");
        assertEquals(ReactionEligibilityPolicy.Reason.ROUND_USE_EXHAUSTED,
                resolver.evaluate("alpha", "attack_of_opportunity", policy).eligibility().orElseThrow().reason());

        rounds.startRound();
        assertEquals(ReactionEligibilityPolicy.Reason.ELIGIBLE,
                resolver.evaluate("alpha", "attack_of_opportunity", policy).eligibility().orElseThrow().reason());
    }

    @Test
    void canonicalMovesetWithoutReactionProducesMissingOwnership() {
        BattleRuntimeState state = state(Set.of(), "Tackle");
        RuntimeReactionUsageTracker tracker = new RuntimeReactionUsageTracker(state);
        RuntimeReactionEligibilityResolver resolver = new RuntimeReactionEligibilityResolver(state, tracker);

        RuntimeReactionEligibilityResolver.Resolution result = resolver.evaluate(
                "alpha",
                "attack_of_opportunity",
                ReactionEligibilityPolicy.attackOfOpportunity()
        );

        assertTrue(result.ownershipKnown());
        assertEquals(RuntimeReactionOwnershipResolver.Status.NOT_OWNED, result.ownership().status());
        assertEquals(
                ReactionEligibilityPolicy.Reason.MISSING_OWNERSHIP,
                result.eligibility().orElseThrow().reason()
        );
    }

    @Test
    void partialSnapshotPreservesUnknownOwnershipWithoutEvaluatingPolicy() {
        BattleRuntimeState partial = new BattleRuntimeState(
                new MovementGrid(2, 1, Set.of(), Map.of()),
                List.of(alpha())
        );
        RuntimeReactionUsageTracker tracker = new RuntimeReactionUsageTracker(partial);
        RuntimeReactionEligibilityResolver resolver = new RuntimeReactionEligibilityResolver(partial, tracker);

        RuntimeReactionEligibilityResolver.Resolution result = resolver.evaluate(
                "alpha",
                "attack_of_opportunity",
                ReactionEligibilityPolicy.attackOfOpportunity()
        );

        assertFalse(result.ownershipKnown());
        assertEquals(RuntimeReactionOwnershipResolver.Status.UNKNOWN, result.ownership().status());
        assertTrue(result.eligibility().isEmpty());
    }
}
