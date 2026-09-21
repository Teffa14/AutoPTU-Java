package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveRuntimeExecutionContextTest {
    @Test
    void projectsAllOwnershipFromOneExecutionIdentity() {
        assertContext(MoveRuntimeExecutionMode.ORDINARY, true, true, false);
        assertContext(MoveRuntimeExecutionMode.PRE_RESOLUTION_RESOLVED, true, true, true);
        assertContext(MoveRuntimeExecutionMode.AREA_RESOLVED, false, true, true);
        assertContext(MoveRuntimeExecutionMode.DELAYED, false, false, true);
        assertContext(MoveRuntimeExecutionMode.COMMITTED_REACTION, false, true, true);
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
        assertEquals(MoveRuntimeExecutionMode.AREA_RESOLVED, area.mode());
        assertEquals(MoveRuntimeExecutionMode.COMMITTED_REACTION, reaction.mode());
    }

    private static void assertContext(
            MoveRuntimeExecutionMode mode,
            boolean spendsResources,
            boolean runsReactions,
            boolean declarationAlreadyValidated
    ) {
        MoveRuntimeExecutionContext context = MoveRuntimeExecutionContext.of(mode);
        assertEquals(mode, context.mode());
        if (spendsResources) assertTrue(context.spendOrdinaryMoveResources());
        else assertFalse(context.spendOrdinaryMoveResources());
        if (runsReactions) assertTrue(context.runPreDamageReactions());
        else assertFalse(context.runPreDamageReactions());
        if (declarationAlreadyValidated) assertTrue(context.declarationAlreadyValidated());
        else assertFalse(context.declarationAlreadyValidated());
    }
}
