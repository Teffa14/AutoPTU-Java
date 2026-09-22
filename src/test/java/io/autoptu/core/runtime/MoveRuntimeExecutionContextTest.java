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
                true, true, true, false);
        assertContext(
                MoveRuntimeExecutionMode.PRE_RESOLUTION_RESOLVED,
                MoveRuntimeExecutionMode.DeclarationValidation.ALREADY_VALIDATED,
                true, true, true, true);
        assertContext(
                MoveRuntimeExecutionMode.AREA_RESOLVED,
                MoveRuntimeExecutionMode.DeclarationValidation.AREA_RESOLVED,
                false, false, true, true);
        assertContext(
                MoveRuntimeExecutionMode.DELAYED,
                MoveRuntimeExecutionMode.DeclarationValidation.DELAYED,
                false, false, false, true);
        assertContext(
                MoveRuntimeExecutionMode.COMMITTED_REACTION,
                MoveRuntimeExecutionMode.DeclarationValidation.ALREADY_VALIDATED,
                false, false, true, true);
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
    void actionSpendAndFrequencyOwnershipAreProjectedIndependently() {
        for (MoveRuntimeExecutionMode mode : MoveRuntimeExecutionMode.values()) {
            MoveRuntimeExecutionContext context = MoveRuntimeExecutionContext.of(mode);
            assertEquals(context.ownsActionSpend(), context.ownsMoveFrequency(), mode.name());
        }
    }

    @Test
    void preservesDistinctIdentityWhenOwnershipValuesOverlap() {
        MoveRuntimeExecutionContext area = MoveRuntimeExecutionContext.areaResolved();
        MoveRuntimeExecutionContext reaction = MoveRuntimeExecutionContext.committedReaction();

        assertEquals(area.ownsActionSpend(), reaction.ownsActionSpend());
        assertEquals(area.ownsMoveFrequency(), reaction.ownsMoveFrequency());
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
            boolean ownsActionSpend,
            boolean ownsMoveFrequency,
            boolean runsReactions,
            boolean declarationAlreadyValidated
    ) {
        MoveRuntimeExecutionContext context = MoveRuntimeExecutionContext.of(mode);
        assertEquals(mode, context.mode());
        assertEquals(declarationValidation, context.declarationValidation());
        if (ownsActionSpend) assertTrue(context.ownsActionSpend());
        else assertFalse(context.ownsActionSpend());
        if (ownsMoveFrequency) assertTrue(context.ownsMoveFrequency());
        else assertFalse(context.ownsMoveFrequency());
        if (runsReactions) assertTrue(context.runPreDamageReactions());
        else assertFalse(context.runPreDamageReactions());
        if (declarationAlreadyValidated) assertTrue(context.declarationAlreadyValidated());
        else assertFalse(context.declarationAlreadyValidated());
    }
}
