package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BattleRuntimeExecutionContextOwnershipTest {
    @Test
    void freezesResolverOwnershipForEveryExecutionIdentity() {
        assertContext(MoveRuntimeExecutionContext.ordinary(),
                MoveRuntimeExecutionMode.ORDINARY, true, true, false);
        assertContext(MoveRuntimeExecutionContext.preResolutionResolved(),
                MoveRuntimeExecutionMode.PRE_RESOLUTION_RESOLVED, true, true, true);
        assertContext(MoveRuntimeExecutionContext.areaResolved(),
                MoveRuntimeExecutionMode.AREA_RESOLVED, false, true, true);
        assertContext(MoveRuntimeExecutionContext.delayed(),
                MoveRuntimeExecutionMode.DELAYED, false, false, true);
        assertContext(MoveRuntimeExecutionContext.committedReaction(),
                MoveRuntimeExecutionMode.COMMITTED_REACTION, false, true, true);
    }

    @Test
    void committedReactionAndAreaRemainDistinctDespiteSharedOwnership() {
        MoveRuntimeExecutionContext area = MoveRuntimeExecutionContext.areaResolved();
        MoveRuntimeExecutionContext reaction = MoveRuntimeExecutionContext.committedReaction();

        assertEquals(area.ownsActionSpend(), reaction.ownsActionSpend());
        assertEquals(area.ownsMoveFrequency(), reaction.ownsMoveFrequency());
        assertEquals(area.runPreDamageReactions(), reaction.runPreDamageReactions());
        assertTrue(area.declarationAlreadyValidated());
        assertTrue(reaction.declarationAlreadyValidated());
        assertEquals(MoveRuntimeExecutionMode.DeclarationValidation.AREA_RESOLVED, area.declarationValidation());
        assertEquals(MoveRuntimeExecutionMode.DeclarationValidation.ALREADY_VALIDATED, reaction.declarationValidation());
        assertFalse(area.mode() == reaction.mode());
    }

    private static void assertContext(
            MoveRuntimeExecutionContext context,
            MoveRuntimeExecutionMode mode,
            boolean ownsOrdinaryResources,
            boolean runsPreDamageReactions,
            boolean declarationAlreadyValidated
    ) {
        assertEquals(mode, context.mode());
        assertEquals(ownsOrdinaryResources, context.ownsActionSpend());
        assertEquals(ownsOrdinaryResources, context.ownsMoveFrequency());
        assertEquals(runsPreDamageReactions, context.runPreDamageReactions());
        assertEquals(declarationAlreadyValidated, context.declarationAlreadyValidated());
    }
}
