package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveRuntimeExecutionContextTest {
    @Test
    void projectsAllOwnershipFromOneExecutionIdentity() {
        assertContext(
                MoveRuntimeExecutionMode.ORDINARY,
                MoveRuntimeExecutionMode.DeclarationValidation.ORDINARY,
                true, true, false);
        assertContext(
                MoveRuntimeExecutionMode.PRE_RESOLUTION_RESOLVED,
                MoveRuntimeExecutionMode.DeclarationValidation.ALREADY_VALIDATED,
                true, true, true);
        assertContext(
                MoveRuntimeExecutionMode.AREA_RESOLVED,
                MoveRuntimeExecutionMode.DeclarationValidation.AREA_RESOLVED,
                false, true, true);
        assertContext(
                MoveRuntimeExecutionMode.DELAYED,
                MoveRuntimeExecutionMode.DeclarationValidation.DELAYED,
                false, false, true);
        assertContext(
                MoveRuntimeExecutionMode.COMMITTED_REACTION,
                MoveRuntimeExecutionMode.DeclarationValidation.ALREADY_VALIDATED,
                false, true, true);
    }

    @Test
    void namedIngressFactoriesPreserveExecutionIdentity() {
        assertEquals(MoveRuntimeExecutionMode.ORDINARY, MoveRuntimeExecutionContext.ordinary().mode());
        assertEquals(MoveRuntimeExecutionMode.PRE_RESOLUTION_RESOLVED, MoveRuntimeExecutionContext.preResolutionResolved().mode());
        assertEquals(MoveRuntimeExecutionMode.AREA_RESOLVED, MoveRuntimeExecutionContext.areaResolved().mode());
        assertEquals(MoveRuntimeExecutionMode.DELAYED, MoveRuntimeExecutionContext.delayed().mode());
        assertEquals(MoveRuntimeExecutionMode.COMMITTED_REACTION, MoveRuntimeExecutionContext.committedReaction().mode());
    }

    @Test
    void preservesDistinctIdentityWhenOwnershipValuesOverlap() {
        MoveRuntimeExecutionContext area = MoveRuntimeExecutionContext.areaResolved();
        MoveRuntimeExecutionContext reaction = MoveRuntimeExecutionContext.committedReaction();

        assertEquals(area.spendOrdinaryMoveResources(), reaction.spendOrdinaryMoveResources());
        assertEquals(area.runPreDamageReactions(), reaction.runPreDamageReactions());
        assertEquals(area.declarationAlreadyValidated(), reaction.declarationAlreadyValidated());
        assertEquals(MoveRuntimeExecutionMode.DeclarationValidation.AREA_RESOLVED, area.declarationValidation());
        assertEquals(MoveRuntimeExecutionMode.DeclarationValidation.ALREADY_VALIDATED, reaction.declarationValidation());
        assertEquals(MoveRuntimeExecutionMode.AREA_RESOLVED, area.mode());
        assertEquals(MoveRuntimeExecutionMode.COMMITTED_REACTION, reaction.mode());
    }

    private static void assertContext(
            MoveRuntimeExecutionMode mode,
            MoveRuntimeExecutionMode.DeclarationValidation declarationValidation,
            boolean spendsResources,
            boolean runsReactions,
            boolean declarationAlreadyValidated
    ) {
        MoveRuntimeExecutionContext context = MoveRuntimeExecutionContext.of(mode);
        assertEquals(mode, context.mode());
        assertEquals(declarationValidation, context.declarationValidation());
        if (spendsResources) assertTrue(context.spendOrdinaryMoveResources());
        else assertFalse(context.spendOrdinaryMoveResources());
        if (runsReactions) assertTrue(context.runPreDamageReactions());
        else assertFalse(context.runPreDamageReactions());
        if (declarationAlreadyValidated) assertTrue(context.declarationAlreadyValidated());
        else assertFalse(context.declarationAlreadyValidated());
    }
}
