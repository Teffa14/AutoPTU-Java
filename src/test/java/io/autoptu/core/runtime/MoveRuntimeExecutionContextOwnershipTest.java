package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression contract for the stateful BattleRuntime migration from independent booleans to one
 * MoveRuntimeExecutionContext. These assertions intentionally freeze the ownership dimensions
 * consumed by the authoritative resolver before its call sites are migrated.
 */
class MoveRuntimeExecutionContextOwnershipTest {

    @Test
    void ordinaryOwnsResourcesAndPreDamageReactions() {
        MoveRuntimeExecutionContext context = MoveRuntimeExecutionContext.ordinary();
        assertAll(
                () -> assertTrue(context.spendOrdinaryMoveResources()),
                () -> assertTrue(context.runPreDamageReactions()),
                () -> assertFalse(context.declarationAlreadyValidated())
        );
    }

    @Test
    void preResolutionResolvedKeepsOrdinaryOwnershipAfterDeclarationValidation() {
        MoveRuntimeExecutionContext context = MoveRuntimeExecutionContext.preResolutionResolved();
        assertAll(
                () -> assertTrue(context.spendOrdinaryMoveResources()),
                () -> assertTrue(context.runPreDamageReactions()),
                () -> assertTrue(context.declarationAlreadyValidated())
        );
    }

    @Test
    void areaResolvedRunsPreDamageReactionsWithoutOwningOrdinaryResources() {
        MoveRuntimeExecutionContext context = MoveRuntimeExecutionContext.areaResolved();
        assertAll(
                () -> assertFalse(context.spendOrdinaryMoveResources()),
                () -> assertTrue(context.runPreDamageReactions()),
                () -> assertTrue(context.declarationAlreadyValidated())
        );
    }

    @Test
    void delayedOwnsNeitherOrdinaryResourcesNorPreDamageReactions() {
        MoveRuntimeExecutionContext context = MoveRuntimeExecutionContext.delayed();
        assertAll(
                () -> assertFalse(context.spendOrdinaryMoveResources()),
                () -> assertFalse(context.runPreDamageReactions()),
                () -> assertTrue(context.declarationAlreadyValidated())
        );
    }

    @Test
    void committedReactionKeepsDistinctValidatedIdentityWithAreaLikeOwnership() {
        MoveRuntimeExecutionContext context = MoveRuntimeExecutionContext.committedReaction();
        assertAll(
                () -> assertFalse(context.spendOrdinaryMoveResources()),
                () -> assertTrue(context.runPreDamageReactions()),
                () -> assertTrue(context.declarationAlreadyValidated())
        );
    }
}
