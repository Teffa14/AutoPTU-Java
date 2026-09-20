package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommittedReactionRuntimeIngressTest {
    @Test
    void rejectsMissingValidatedPlanBeforeResolverRuns() {
        assertThrows(NullPointerException.class, () ->
                CommittedReactionRuntimeIngress.resolve(null, (plan, spend, preDamage, validated) -> "unreachable")
        );
    }

    @Test
    void committedReactionDispatchHasExplicitOwnershipMode() {
        CommittedReactionRuntimeExecutionPlan plan = CommittedReactionRuntimeExecutionPlanTestFixtures.validPlan();

        CommittedReactionRuntimeIngress.Dispatch dispatch = CommittedReactionRuntimeIngress.dispatch(plan);

        assertEquals(CommittedReactionRuntimeIngress.ExecutionMode.COMMITTED_REACTION, dispatch.executionMode());
        assertFalse(dispatch.spendOrdinaryMoveResources());
        assertTrue(dispatch.runPreDamageReactions());
        assertTrue(dispatch.declaredChoiceAlreadyValidated());
    }
}
