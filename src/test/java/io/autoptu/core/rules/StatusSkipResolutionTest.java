package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StatusSkipResolutionTest {
    @Test
    void marksBaseStandardAndShiftBuckets() {
        ActionBudget budget = new ActionBudget();

        StatusSkipResolution.apply(budget);

        assertFalse(budget.hasActionAvailable(ActionType.STANDARD));
        assertFalse(budget.hasActionAvailable(ActionType.SHIFT));
        assertEquals("Status skip", budget.consumedDetail(ActionType.STANDARD).orElseThrow());
        assertEquals("Status skip", budget.consumedDetail(ActionType.SHIFT).orElseThrow());
    }

    @Test
    void preservesExistingActionDetailsAndDoesNotConsumeExtras() {
        ActionBudget budget = new ActionBudget();
        budget.markAction(ActionType.STANDARD, "Tackle");
        budget.grantExtra(ActionType.STANDARD, 2);
        budget.grantExtra(ActionType.SHIFT, 1);

        StatusSkipResolution.apply(budget);

        assertEquals("Tackle", budget.consumedDetail(ActionType.STANDARD).orElseThrow());
        assertEquals("Status skip", budget.consumedDetail(ActionType.SHIFT).orElseThrow());
        assertEquals(2, budget.extraCount(ActionType.STANDARD));
        assertEquals(1, budget.extraCount(ActionType.SHIFT));
    }
}
