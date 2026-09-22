package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommittedReactionRuntimeIngressTest {
    @Test
    void rejectsMissingValidatedPlanBeforeContextResolverRuns() {
        assertThrows(NullPointerException.class, () ->
                CommittedReactionRuntimeIngress.resolveExecutionContext(null, (plan, context) -> "unreachable")
        );
    }

    @Test
    void committedReactionDispatchCarriesOneExecutionContextIdentity() {
        CommittedReactionRuntimeIngress.Dispatch dispatch = new CommittedReactionRuntimeIngress.Dispatch(
                MoveRuntimeExecutionContext.committedReaction()
        );

        assertEquals(MoveRuntimeExecutionMode.COMMITTED_REACTION, dispatch.executionContext().mode());
        assertEquals(MoveRuntimeExecutionMode.COMMITTED_REACTION, dispatch.executionMode());
        assertFalse(dispatch.ownsActionSpend());
        assertFalse(dispatch.ownsMoveFrequency());
        assertFalse(dispatch.spendOrdinaryMoveResources());
        assertTrue(dispatch.executionContext().runPreDamageReactions());
        assertTrue(dispatch.executionContext().declarationAlreadyValidated());
    }

    @Test
    void legacyTupleCannotDescribeCommittedReactionWithDifferentOwnership() {
        assertThrows(IllegalArgumentException.class, () -> new CommittedReactionRuntimeIngress.Dispatch(
                MoveRuntimeExecutionMode.COMMITTED_REACTION,
                true,
                true,
                true
        ));
    }
}
