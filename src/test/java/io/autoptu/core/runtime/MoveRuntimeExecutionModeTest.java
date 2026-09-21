package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveRuntimeExecutionModeTest {
    @Test
    void ordinaryMoveOwnsResourcesAndDeclarationValidation() {
        MoveRuntimeExecutionMode mode = MoveRuntimeExecutionMode.ORDINARY;
        assertEquals(MoveRuntimeExecutionMode.DeclarationValidation.ORDINARY, mode.declarationValidation());
        assertTrue(mode.spendOrdinaryMoveResources());
        assertTrue(mode.runPreDamageReactions());
        assertFalse(mode.declarationAlreadyValidated());
    }

    @Test
    void preResolutionResolvedMoveSpendsOrdinaryResourcesWithoutRevalidatingReplacedTarget() {
        MoveRuntimeExecutionMode mode = MoveRuntimeExecutionMode.PRE_RESOLUTION_RESOLVED;
        assertEquals(MoveRuntimeExecutionMode.DeclarationValidation.ALREADY_VALIDATED, mode.declarationValidation());
        assertTrue(mode.spendOrdinaryMoveResources());
        assertTrue(mode.runPreDamageReactions());
        assertTrue(mode.declarationAlreadyValidated());
    }

    @Test
    void areaResolvedTargetKeepsReactionsWithoutSecondResourceSpend() {
        MoveRuntimeExecutionMode mode = MoveRuntimeExecutionMode.AREA_RESOLVED;
        assertEquals(MoveRuntimeExecutionMode.DeclarationValidation.AREA_RESOLVED, mode.declarationValidation());
        assertFalse(mode.spendOrdinaryMoveResources());
        assertTrue(mode.runPreDamageReactions());
        assertTrue(mode.declarationAlreadyValidated());
    }

    @Test
    void delayedHitSkipsOrdinaryResourcesAndPreDamageReactionWindow() {
        MoveRuntimeExecutionMode mode = MoveRuntimeExecutionMode.DELAYED;
        assertEquals(MoveRuntimeExecutionMode.DeclarationValidation.DELAYED, mode.declarationValidation());
        assertFalse(mode.spendOrdinaryMoveResources());
        assertFalse(mode.runPreDamageReactions());
        assertTrue(mode.declarationAlreadyValidated());
    }

    @Test
    void committedReactionSkipsOnlyDeclarationValidation() {
        MoveRuntimeExecutionMode mode = MoveRuntimeExecutionMode.COMMITTED_REACTION;
        assertEquals(MoveRuntimeExecutionMode.DeclarationValidation.ALREADY_VALIDATED, mode.declarationValidation());
        assertFalse(mode.spendOrdinaryMoveResources());
        assertTrue(mode.runPreDamageReactions());
        assertTrue(mode.declarationAlreadyValidated());
    }

    @Test
    void legacyUnambiguousTuplesMapToTheirExecutionIdentity() {
        assertEquals(MoveRuntimeExecutionMode.ORDINARY,
                MoveRuntimeExecutionMode.requireLegacyTuple(true, true, false));
        assertEquals(MoveRuntimeExecutionMode.PRE_RESOLUTION_RESOLVED,
                MoveRuntimeExecutionMode.requireLegacyTuple(true, true, true));
        assertEquals(MoveRuntimeExecutionMode.AREA_RESOLVED,
                MoveRuntimeExecutionMode.requireLegacyTuple(false, true, false));
        assertEquals(MoveRuntimeExecutionMode.DELAYED,
                MoveRuntimeExecutionMode.requireLegacyTuple(false, false, false));
    }

    @Test
    void committedReactionCannotBeReconstructedFromLegacyBooleans() {
        assertThrows(IllegalArgumentException.class,
                () -> MoveRuntimeExecutionMode.requireLegacyTuple(false, true, true));
    }

    @Test
    void unsupportedLegacyTuplesFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> MoveRuntimeExecutionMode.requireLegacyTuple(true, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> MoveRuntimeExecutionMode.requireLegacyTuple(false, false, true));
    }
}
