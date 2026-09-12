package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionEconomyProfileConformanceTest {
    @Test
    void pythonCompatibilityKeepsFullIndependentFromStandardAndShift() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY);

        assertTrue(budget.consume(ActionType.FULL, "python full"));
        assertFalse(budget.hasActionAvailable(ActionType.FULL));
        assertTrue(budget.hasActionAvailable(ActionType.STANDARD));
        assertTrue(budget.hasActionAvailable(ActionType.SHIFT));
        assertFalse(budget.standardConversion().isPresent());
    }

    @Test
    void kairosFullAtomicallyConsumesBaseStandardAndShift() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);

        assertTrue(budget.consume(ActionType.FULL, "kairos full"));

        assertFalse(budget.hasActionAvailable(ActionType.FULL));
        assertFalse(budget.hasActionAvailable(ActionType.STANDARD));
        assertFalse(budget.hasActionAvailable(ActionType.SHIFT));
        assertEquals("kairos full", budget.consumedDetail(ActionType.STANDARD).orElseThrow());
        assertEquals("kairos full", budget.consumedDetail(ActionType.SHIFT).orElseThrow());
    }

    @Test
    void kairosFullFailsWithoutMutatingWhenEitherBaseResourceWasSpent() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);
        assertTrue(budget.consume(ActionType.STANDARD, "standard first"));

        assertFalse(budget.consume(ActionType.FULL, "too late"));
        assertTrue(budget.hasActionAvailable(ActionType.SHIFT));
        assertFalse(budget.consumedDetail(ActionType.FULL).isPresent());
    }

    @Test
    void kairosStandardConvertsToAdditionalSwift() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);

        assertTrue(budget.consume(ActionType.SWIFT, "base swift"));
        assertTrue(budget.hasCapacity(ActionType.SWIFT));
        assertTrue(budget.consume(ActionType.SWIFT, "converted swift"));

        assertEquals(ActionType.SWIFT, budget.standardConversion().orElseThrow());
        assertFalse(budget.hasActionAvailable(ActionType.STANDARD));
        assertFalse(budget.hasCapacity(ActionType.SWIFT));
    }

    @Test
    void kairosStandardConvertsToAdditionalNonMovementShift() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);

        assertTrue(budget.consume(ActionType.SHIFT, "base shift effect"));
        assertTrue(budget.consume(ActionType.SHIFT, "converted shift effect"));

        assertEquals(ActionType.SHIFT, budget.standardConversion().orElseThrow());
        assertFalse(budget.hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void kairosStandardCannotFundSecondMovementAfterRegularMovementShift() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);

        assertTrue(budget.consumeMovement("base movement"));
        assertTrue(budget.regularShiftUsedForMovement());
        assertFalse(budget.hasCapacity(ActionType.SHIFT, true));
        assertFalse(budget.consumeMovement("second movement"));
        assertTrue(budget.hasActionAvailable(ActionType.STANDARD));
        assertTrue(budget.consume(ActionType.SHIFT, "non-movement shift effect"));
    }

    @Test
    void kairosStandardCanFundMovementWhenRegularShiftWasNonMovement() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);

        assertTrue(budget.consume(ActionType.SHIFT, "base shift effect"));
        assertTrue(budget.consumeMovement("converted movement"));

        assertEquals(ActionType.SHIFT, budget.standardConversion().orElseThrow());
        assertFalse(budget.regularShiftUsedForMovement());
    }

    @Test
    void kairosExtraFullDoesNotReplaceMissingBaseResources() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);
        budget.consume(ActionType.STANDARD, "spent standard");
        budget.grantExtra(ActionType.FULL, 1);

        assertFalse(budget.hasCapacity(ActionType.FULL));
        assertFalse(budget.consume(ActionType.FULL, "extra full"));
        assertEquals(1, budget.extraCount(ActionType.FULL));
    }
}
