package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BattleRuntimeExecutionContextOwnershipTest {
    @Test
    void freezesResolverOwnershipForEveryExecutionIdentity() {
        assertContext(MoveRuntimeExecutionContext.ordinary(),
                MoveRuntimeExecutionMode.ORDINARY, true, true, true, false,
                MoveRuntimeExecutionMode.DeclarationValidation.ORDINARY);
        assertContext(MoveRuntimeExecutionContext.preResolutionResolved(),
                MoveRuntimeExecutionMode.PRE_RESOLUTION_RESOLVED, true, true, true, true,
                MoveRuntimeExecutionMode.DeclarationValidation.ALREADY_VALIDATED);
        assertContext(MoveRuntimeExecutionContext.areaResolved(),
                MoveRuntimeExecutionMode.AREA_RESOLVED, false, false, true, true,
                MoveRuntimeExecutionMode.DeclarationValidation.AREA_RESOLVED);
        assertContext(MoveRuntimeExecutionContext.delayed(),
                MoveRuntimeExecutionMode.DELAYED, false, false, false, true,
                MoveRuntimeExecutionMode.DeclarationValidation.DELAYED);
        assertContext(MoveRuntimeExecutionContext.committedReaction(),
                MoveRuntimeExecutionMode.COMMITTED_REACTION, false, false, true, true,
                MoveRuntimeExecutionMode.DeclarationValidation.ALREADY_VALIDATED);
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

    @Test
    void executionContextProjectsEveryModeDecisionWithoutReconstruction() {
        for (MoveRuntimeExecutionMode mode : MoveRuntimeExecutionMode.values()) {
            MoveRuntimeExecutionContext context = MoveRuntimeExecutionContext.of(mode);
            assertEquals(mode, context.mode());
            assertEquals(mode.ownsActionSpend(), context.ownsActionSpend());
            assertEquals(mode.ownsMoveFrequency(), context.ownsMoveFrequency());
            assertEquals(mode.runPreDamageReactions(), context.runPreDamageReactions());
            assertEquals(mode.declarationAlreadyValidated(), context.declarationAlreadyValidated());
            assertEquals(mode.declarationValidation(), context.declarationValidation());
        }
    }

    private static void assertContext(
            MoveRuntimeExecutionContext context,
            MoveRuntimeExecutionMode mode,
            boolean ownsActionSpend,
            boolean ownsMoveFrequency,
            boolean runsPreDamageReactions,
            boolean declarationAlreadyValidated,
            MoveRuntimeExecutionMode.DeclarationValidation declarationValidation
    ) {
        assertEquals(mode, context.mode());
        assertEquals(ownsActionSpend, context.ownsActionSpend());
        assertEquals(ownsMoveFrequency, context.ownsMoveFrequency());
        assertEquals(runsPreDamageReactions, context.runPreDamageReactions());
        assertEquals(declarationAlreadyValidated, context.declarationAlreadyValidated());
        assertEquals(declarationValidation, context.declarationValidation());
    }
}
