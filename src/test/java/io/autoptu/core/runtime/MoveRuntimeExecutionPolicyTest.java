package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveRuntimeExecutionPolicyTest {
    @Test
    void projectsOwnershipFromOneExecutionIdentity() {
        assertPolicy(MoveRuntimeExecutionMode.ORDINARY, true, true,
                MoveRuntimeExecutionMode.DeclarationValidation.ORDINARY, false);
        assertPolicy(MoveRuntimeExecutionMode.PRE_RESOLUTION_RESOLVED, true, true,
                MoveRuntimeExecutionMode.DeclarationValidation.ALREADY_VALIDATED, true);
        assertPolicy(MoveRuntimeExecutionMode.AREA_RESOLVED, false, true,
                MoveRuntimeExecutionMode.DeclarationValidation.AREA_RESOLVED, true);
        assertPolicy(MoveRuntimeExecutionMode.DELAYED, false, false,
                MoveRuntimeExecutionMode.DeclarationValidation.DELAYED, true);
        assertPolicy(MoveRuntimeExecutionMode.COMMITTED_REACTION, false, true,
                MoveRuntimeExecutionMode.DeclarationValidation.ALREADY_VALIDATED, true);
    }

    @Test
    void committedReactionRetainsDistinctIdentityFromAreaResolved() {
        MoveRuntimeExecutionPolicy reaction = MoveRuntimeExecutionPolicy.of(
                MoveRuntimeExecutionMode.COMMITTED_REACTION);
        MoveRuntimeExecutionPolicy area = MoveRuntimeExecutionPolicy.of(
                MoveRuntimeExecutionMode.AREA_RESOLVED);

        assertEquals(MoveRuntimeExecutionMode.COMMITTED_REACTION, reaction.mode());
        assertEquals(MoveRuntimeExecutionMode.AREA_RESOLVED, area.mode());
        assertEquals(reaction.ownsActionSpend(), area.ownsActionSpend());
        assertEquals(reaction.ownsMoveFrequency(), area.ownsMoveFrequency());
        assertEquals(reaction.runPreDamageReactions(), area.runPreDamageReactions());
        assertEquals(MoveRuntimeExecutionMode.DeclarationValidation.ALREADY_VALIDATED,
                reaction.declarationValidation());
        assertEquals(MoveRuntimeExecutionMode.DeclarationValidation.AREA_RESOLVED,
                area.declarationValidation());
    }

    @Test
    void preResolvedTargetRetainsDistinctIdentityFromOrdinaryExecution() {
        MoveRuntimeExecutionPolicy preResolved = MoveRuntimeExecutionPolicy.of(
                MoveRuntimeExecutionMode.PRE_RESOLUTION_RESOLVED);
        MoveRuntimeExecutionPolicy ordinary = MoveRuntimeExecutionPolicy.of(
                MoveRuntimeExecutionMode.ORDINARY);

        assertEquals(MoveRuntimeExecutionMode.PRE_RESOLUTION_RESOLVED, preResolved.mode());
        assertEquals(MoveRuntimeExecutionMode.ORDINARY, ordinary.mode());
        assertEquals(preResolved.ownsActionSpend(), ordinary.ownsActionSpend());
        assertEquals(preResolved.ownsMoveFrequency(), ordinary.ownsMoveFrequency());
        assertEquals(preResolved.runPreDamageReactions(), ordinary.runPreDamageReactions());
        assertTrue(preResolved.declarationAlreadyValidated());
        assertFalse(ordinary.declarationAlreadyValidated());
    }

    private static void assertPolicy(
            MoveRuntimeExecutionMode mode,
            boolean ownsActionSpend,
            boolean ownsMoveFrequency,
            MoveRuntimeExecutionMode.DeclarationValidation declarationValidation,
            boolean declarationAlreadyValidated
    ) {
        MoveRuntimeExecutionPolicy policy = MoveRuntimeExecutionPolicy.of(mode);
        assertEquals(mode, policy.mode());
        assertEquals(declarationValidation, policy.declarationValidation());
        assertEquals(declarationAlreadyValidated, policy.declarationAlreadyValidated());
        if (ownsActionSpend) {
            assertTrue(policy.ownsActionSpend());
        } else {
            assertFalse(policy.ownsActionSpend());
        }
        if (ownsMoveFrequency) {
            assertTrue(policy.ownsMoveFrequency());
        } else {
            assertFalse(policy.ownsMoveFrequency());
        }
    }
}
