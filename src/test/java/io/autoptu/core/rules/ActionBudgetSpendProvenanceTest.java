package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionBudgetSpendProvenanceTest {
    @Test
    void baseSpendReportsBaseSourceWithoutChangingLegacySemantics() {
        ActionBudget budget = new ActionBudget();

        ActionSpendResult result = budget.consumeDetailed(ActionType.STANDARD, "move:basic");

        assertTrue(result.consumed());
        assertEquals(ActionSpendResult.Source.BASE, result.source());
        assertTrue(result.extraGrantName().isEmpty());
        assertFalse(budget.hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void namedExtraSpendReturnsExactDeterministicGrantIdentity() {
        ActionBudget budget = new ActionBudget();
        budget.markAction(ActionType.SWIFT, "base-swift");
        budget.grantExtra(ActionType.SWIFT, "feature:quick-switch", 1);
        budget.grantExtra(ActionType.SWIFT, "item:tempo", 1);

        ActionSpendResult first = budget.consumeDetailed(ActionType.SWIFT, "extra-one");
        ActionSpendResult second = budget.consumeDetailed(ActionType.SWIFT, "extra-two");

        assertEquals(ActionSpendResult.Source.EXTRA, first.source());
        assertEquals("feature:quick-switch", first.extraGrantName().orElseThrow());
        assertEquals(ActionSpendResult.Source.EXTRA, second.source());
        assertEquals("item:tempo", second.extraGrantName().orElseThrow());
        assertEquals(0, budget.extraCount(ActionType.SWIFT));
    }

    @Test
    void kairosStandardConversionReportsConversionSource() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);
        budget.markAction(ActionType.SWIFT, "base-swift");

        ActionSpendResult result = budget.consumeDetailed(ActionType.SWIFT, "converted-swift");

        assertTrue(result.consumed());
        assertEquals(ActionSpendResult.Source.STANDARD_CONVERSION, result.source());
        assertEquals(ActionType.SWIFT, budget.standardConversion().orElseThrow());
        assertFalse(budget.hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void movementSpendPreservesKairosSecondMovementRestriction() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);

        ActionSpendResult first = budget.consumeMovementDetailed("first-movement");
        ActionSpendResult second = budget.consumeMovementDetailed("second-movement");

        assertEquals(ActionSpendResult.Source.BASE, first.source());
        assertEquals(ActionSpendResult.Source.UNAVAILABLE, second.source());
        assertFalse(second.consumed());
        assertTrue(budget.hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void freeAndUnavailableSpendsAreExplicit() {
        ActionBudget budget = new ActionBudget();

        ActionSpendResult free = budget.consumeDetailed(ActionType.FREE, "free-effect");
        budget.markAction(ActionType.FULL, "base-full");
        ActionSpendResult unavailable = budget.consumeDetailed(ActionType.FULL, "second-full");

        assertEquals(ActionSpendResult.Source.FREE, free.source());
        assertTrue(free.consumed());
        assertEquals(ActionSpendResult.Source.UNAVAILABLE, unavailable.source());
        assertFalse(unavailable.consumed());
    }
}
