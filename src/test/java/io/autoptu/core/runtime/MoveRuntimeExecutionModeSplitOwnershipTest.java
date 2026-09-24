package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MoveRuntimeExecutionModeSplitOwnershipTest {
    @Test
    void freezesIndependentOwnershipForEveryExecutionIdentity() {
        assertOwnership(MoveRuntimeExecutionMode.ORDINARY, true, true, true);
        assertOwnership(MoveRuntimeExecutionMode.PRE_RESOLUTION_RESOLVED, true, true, true);
        assertOwnership(MoveRuntimeExecutionMode.AREA_RESOLVED, false, false, true);
        assertOwnership(MoveRuntimeExecutionMode.DELAYED, false, false, false);
        assertOwnership(MoveRuntimeExecutionMode.COMMITTED_REACTION, false, false, true);
    }

    @Test
    void legacyTupleCannotInferCommittedReactionIdentity() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> MoveRuntimeExecutionMode.requireLegacyTuple(false, true, true)
        );

        assertTrue(error.getMessage().contains("ambiguous"));
    }

    @Test
    void contextKeepsActionAndFrequencyAsSeparateQueries() {
        MoveRuntimeExecutionContext context = MoveRuntimeExecutionContext.committedReaction();

        assertFalse(context.ownsActionSpend());
        assertFalse(context.ownsMoveFrequency());
        assertTrue(context.runPreDamageReactions());
        assertTrue(context.declarationAlreadyValidated());
        assertEquals(MoveRuntimeExecutionMode.COMMITTED_REACTION, context.mode());
    }

    private static void assertOwnership(
            MoveRuntimeExecutionMode mode,
            boolean ownsActionSpend,
            boolean ownsMoveFrequency,
            boolean runPreDamageReactions
    ) {
        MoveRuntimeExecutionContext context = MoveRuntimeExecutionContext.of(mode);
        assertEquals(ownsActionSpend, context.ownsActionSpend());
        assertEquals(ownsMoveFrequency, context.ownsMoveFrequency());
        assertEquals(runPreDamageReactions, context.runPreDamageReactions());
    }
}
