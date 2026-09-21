package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveRuntimeExecutionPolicyTest {
    @Test
    void projectsOwnershipFromOneExecutionIdentity() {
        assertPolicy(MoveRuntimeExecutionMode.ORDINARY, true, true);
        assertPolicy(MoveRuntimeExecutionMode.AREA_RESOLVED, false, true);
        assertPolicy(MoveRuntimeExecutionMode.DELAYED, false, false);
        assertPolicy(MoveRuntimeExecutionMode.COMMITTED_REACTION, false, true);
    }

    @Test
    void committedReactionRetainsDistinctIdentityFromAreaResolved() {
        MoveRuntimeExecutionPolicy reaction = MoveRuntimeExecutionPolicy.of(
                MoveRuntimeExecutionMode.COMMITTED_REACTION);
        MoveRuntimeExecutionPolicy area = MoveRuntimeExecutionPolicy.of(
                MoveRuntimeExecutionMode.AREA_RESOLVED);

        assertEquals(MoveRuntimeExecutionMode.COMMITTED_REACTION, reaction.mode());
        assertEquals(MoveRuntimeExecutionMode.AREA_RESOLVED, area.mode());
        assertEquals(reaction.spendOrdinaryMoveResources(), area.spendOrdinaryMoveResources());
        assertEquals(reaction.runPreDamageReactions(), area.runPreDamageReactions());
    }

    private static void assertPolicy(
            MoveRuntimeExecutionMode mode,
            boolean spendOrdinaryMoveResources,
            boolean runPreDamageReactions
    ) {
        MoveRuntimeExecutionPolicy policy = MoveRuntimeExecutionPolicy.of(mode);
        assertEquals(mode, policy.mode());
        if (spendOrdinaryMoveResources) {
            assertTrue(policy.spendOrdinaryMoveResources());
        } else {
            assertFalse(policy.spendOrdinaryMoveResources());
        }
        if (runPreDamageReactions) {
            assertTrue(policy.runPreDamageReactions());
        } else {
            assertFalse(policy.runPreDamageReactions());
        }
    }
}
