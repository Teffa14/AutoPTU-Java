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
    void committedReactionDispatchCarriesExplicitExecutionOwnership() {
        CommittedReactionRuntimeIngress.Dispatch dispatch = new CommittedReactionRuntimeIngress.Dispatch(
                MoveRuntimeExecutionMode.COMMITTED_REACTION,
                false,
                true,
                true
        );

        assertEquals(MoveRuntimeExecutionMode.COMMITTED_REACTION, dispatch.executionMode());
        assertFalse(dispatch.spendOrdinaryMoveResources());
        assertTrue(dispatch.runPreDamageReactions());
        assertTrue(dispatch.declaredChoiceAlreadyValidated());
    }
}
