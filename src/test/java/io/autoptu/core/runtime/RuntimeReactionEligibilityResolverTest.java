package io.autoptu.core.runtime;

import io.autoptu.core.hook.ReactionEligibilityPolicy;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeReactionEligibilityResolverTest {
    private static BattleRuntimeState state(Set<String> statuses) {
        RuntimeCombatantState alpha = new RuntimeCombatantState(
                "alpha",
                MovementProfile.walking(new GridCoord(0, 0), 5),
                20,
                20,
                new ActionBudget()
        );
        return new BattleRuntimeState(
                new MovementGrid(2, 1, Set.of(), Map.of()),
                List.of(alpha),
                Map.of("alpha", statuses)
        );
    }

    @Test
    void readsBlockingStatusesFromAuthoritativeBattleState() {
        BattleRuntimeState state = state(Set.of("Sleeping"));
        RuntimeReactionUsageTracker tracker = new RuntimeReactionUsageTracker(state);
        RuntimeReactionEligibilityResolver resolver = new RuntimeReactionEligibilityResolver(state, tracker);

        assertEquals(
                ReactionEligibilityPolicy.Reason.BLOCKED_BY_STATUS,
                resolver.evaluate("alpha", "attack_of_opportunity", true, ReactionEligibilityPolicy.attackOfOpportunity()).reason()
        );
    }

    @Test
    void readsUsageFromCanonicalRoundTrackerAndRolloverMakesPriorUseIrrelevant() {
        BattleRuntimeState state = state(Set.of());
        BattleRoundController rounds = new BattleRoundController(state, 1);
        RuntimeReactionUsageTracker tracker = new RuntimeReactionUsageTracker(state);
        RuntimeReactionEligibilityResolver resolver = new RuntimeReactionEligibilityResolver(state, tracker);
        ReactionEligibilityPolicy policy = ReactionEligibilityPolicy.attackOfOpportunity();

        assertEquals(ReactionEligibilityPolicy.Reason.ELIGIBLE,
                resolver.evaluate("alpha", "attack_of_opportunity", true, policy).reason());

        tracker.recordUseFromRuntime("alpha", "attack_of_opportunity");
        assertEquals(ReactionEligibilityPolicy.Reason.ROUND_USE_EXHAUSTED,
                resolver.evaluate("alpha", "attack_of_opportunity", true, policy).reason());

        rounds.startRound();
        assertEquals(ReactionEligibilityPolicy.Reason.ELIGIBLE,
                resolver.evaluate("alpha", "attack_of_opportunity", true, policy).reason());
    }

    @Test
    void preservesContentOwnershipAsAnExplicitInput() {
        BattleRuntimeState state = state(Set.of());
        RuntimeReactionUsageTracker tracker = new RuntimeReactionUsageTracker(state);
        RuntimeReactionEligibilityResolver resolver = new RuntimeReactionEligibilityResolver(state, tracker);

        assertEquals(
                ReactionEligibilityPolicy.Reason.MISSING_OWNERSHIP,
                resolver.evaluate("alpha", "attack_of_opportunity", false, ReactionEligibilityPolicy.attackOfOpportunity()).reason()
        );
    }
}
