package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionBudgetRulebookConformanceTest {
    @Test
    void standardConvertsToOneAdditionalSwift() {
        ActionBudget budget = new ActionBudget();

        assertTrue(budget.consume(ActionType.SWIFT, "first swift"));
        assertTrue(budget.hasCapacity(ActionType.SWIFT));
        assertTrue(budget.consume(ActionType.SWIFT, "converted swift"));

        assertEquals(ActionType.SWIFT, budget.standardConversion().orElseThrow());
        assertFalse(budget.hasActionAvailable(ActionType.STANDARD));
        assertFalse(budget.hasCapacity(ActionType.SWIFT));
    }

    @Test
    void standardConvertsToShiftForNonMovement() {
        ActionBudget budget = new ActionBudget();

        assertTrue(budget.consume(ActionType.SHIFT, "regular shift effect"));
        assertTrue(budget.consume(ActionType.SHIFT, "converted shift effect"));

        assertEquals(ActionType.SHIFT, budget.standardConversion().orElseThrow());
        assertFalse(budget.hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void standardCannotBuySecondMovementAfterRegularShiftMovement() {
        ActionBudget budget = new ActionBudget();

        assertTrue(budget.consumeMovement("first movement"));
        assertTrue(budget.regularShiftUsedForMovement());
        assertFalse(budget.hasCapacity(ActionType.SHIFT, true));
        assertFalse(budget.consumeMovement("second movement"));
        assertTrue(budget.hasActionAvailable(ActionType.STANDARD));
        assertTrue(budget.consume(ActionType.SHIFT, "non-movement shift effect"));
    }

    @Test
    void standardCanBuyMovementWhenRegularShiftPaidForNonMovement() {
        ActionBudget budget = new ActionBudget();

        assertTrue(budget.consume(ActionType.SHIFT, "shift effect"));
        assertTrue(budget.consumeMovement("converted movement"));

        assertEquals(ActionType.SHIFT, budget.standardConversion().orElseThrow());
        assertFalse(budget.regularShiftUsedForMovement());
    }

    @Test
    void namedExtraDoesNotSubstituteForFullActionResources() {
        ActionBudget budget = new ActionBudget();
        budget.markAction(ActionType.STANDARD, "used standard");
        budget.grantExtra(ActionType.STANDARD, "Commander grant", 1);

        assertFalse(budget.hasCapacity(ActionType.FULL));
        assertFalse(budget.consume(ActionType.FULL, "full action"));
        assertEquals(1, budget.extraCount(ActionType.STANDARD, "Commander grant"));
        assertTrue(budget.consume(ActionType.STANDARD, "granted standard"));
    }

    @Test
    void freeActionsRemainUncappedAndUnrecorded() {
        ActionBudget budget = new ActionBudget();

        for (int i = 0; i < 20; i++) {
            assertTrue(budget.consume(ActionType.FREE, "free " + i));
        }

        assertTrue(budget.hasCapacity(ActionType.FREE));
        assertTrue(budget.consumedActions().isEmpty());
    }
}
