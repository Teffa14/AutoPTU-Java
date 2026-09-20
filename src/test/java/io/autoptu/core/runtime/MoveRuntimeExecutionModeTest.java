package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveRuntimeExecutionModeTest {
    @Test
    void ordinaryMoveOwnsResourcesAndDeclarationValidation() {
        MoveRuntimeExecutionMode mode = MoveRuntimeExecutionMode.ORDINARY;

        assertTrue(mode.spendOrdinaryMoveResources());
        assertTrue(mode.runPreDamageReactions());
        assertFalse(mode.declarationAlreadyValidated());
    }

    @Test
    void areaResolvedTargetKeepsReactionsWithoutSecondResourceSpend() {
        MoveRuntimeExecutionMode mode = MoveRuntimeExecutionMode.AREA_RESOLVED;

        assertFalse(mode.spendOrdinaryMoveResources());
        assertTrue(mode.runPreDamageReactions());
        assertTrue(mode.declarationAlreadyValidated());
    }

    @Test
    void delayedHitSkipsOrdinaryResourcesAndPreDamageReactionWindow() {
        MoveRuntimeExecutionMode mode = MoveRuntimeExecutionMode.DELAYED;

        assertFalse(mode.spendOrdinaryMoveResources());
        assertFalse(mode.runPreDamageReactions());
        assertTrue(mode.declarationAlreadyValidated());
    }

    @Test
    void committedReactionIsDistinctFromAreaDespiteSharedResourceOwnership() {
        MoveRuntimeExecutionMode committed = MoveRuntimeExecutionMode.COMMITTED_REACTION;
        MoveRuntimeExecutionMode area = MoveRuntimeExecutionMode.AREA_RESOLVED;

        assertFalse(committed.spendOrdinaryMoveResources());
        assertTrue(committed.runPreDamageReactions());
        assertTrue(committed.declarationAlreadyValidated());
        assertTrue(committed != area);
    }
}
