package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionBudgetExtraGrantProvenanceTest {
    @Test
    void namedExtraGrantsPreserveAggregateAndPerSourceCounts() {
        ActionBudget budget = new ActionBudget();

        budget.grantExtra(ActionType.SWIFT, "feature:quick-switch", 2);
        budget.grantExtra(ActionType.SWIFT, "ability:tempo", 1);

        assertEquals(3, budget.extraCount(ActionType.SWIFT));
        assertEquals(2, budget.extraCount(ActionType.SWIFT, "feature:quick-switch"));
        assertEquals(1, budget.extraCount(ActionType.SWIFT, "ability:tempo"));
        assertEquals(
                List.of("feature:quick-switch", "ability:tempo"),
                List.copyOf(budget.extraGrants(ActionType.SWIFT).keySet())
        );
    }

    @Test
    void genericConsumptionReturnsDeterministicGrantIdentity() {
        ActionBudget budget = new ActionBudget();
        budget.grantExtra(ActionType.SHIFT, "feature:first", 2);
        budget.grantExtra(ActionType.SHIFT, "item:second", 1);

        assertEquals("feature:first", budget.consumeExtraGrant(ActionType.SHIFT).orElseThrow());
        assertEquals(1, budget.extraCount(ActionType.SHIFT, "feature:first"));
        assertEquals("feature:first", budget.consumeExtraGrant(ActionType.SHIFT).orElseThrow());
        assertEquals("item:second", budget.consumeExtraGrant(ActionType.SHIFT).orElseThrow());
        assertTrue(budget.consumeExtraGrant(ActionType.SHIFT).isEmpty());
        assertEquals(Map.of(), budget.extraGrants(ActionType.SHIFT));
    }

    @Test
    void legacyGrantApisRetainAggregateBehavior() {
        ActionBudget budget = new ActionBudget();
        budget.markAction(ActionType.STANDARD, "base-standard");
        budget.grantExtra(ActionType.STANDARD, 2);

        assertEquals(2, budget.extraCount(ActionType.STANDARD));
        assertTrue(budget.consume(ActionType.STANDARD, "extra-one"));
        assertEquals(1, budget.extraCount(ActionType.STANDARD));
        assertTrue(budget.consume(ActionType.STANDARD, "extra-two"));
        assertEquals(0, budget.extraCount(ActionType.STANDARD));
        assertFalse(budget.consume(ActionType.STANDARD, "exhausted"));
    }

    @Test
    void resetClearsNamedExtrasWhileRoundResetKeepsThem() {
        ActionBudget budget = new ActionBudget();
        budget.grantExtra(ActionType.SWIFT, "feature:carry-over", 2);
        budget.markAction(ActionType.SWIFT, "base-swift");

        budget.resetConsumedActions();

        assertEquals(2, budget.extraCount(ActionType.SWIFT, "feature:carry-over"));
        assertTrue(budget.hasActionAvailable(ActionType.SWIFT));

        budget.reset();

        assertEquals(0, budget.extraCount(ActionType.SWIFT));
        assertEquals(Map.of(), budget.extraGrants(ActionType.SWIFT));
    }

    @Test
    void namedGrantRequiresStableSourceIdentity() {
        ActionBudget budget = new ActionBudget();

        assertThrows(
                IllegalArgumentException.class,
                () -> budget.grantExtra(ActionType.SWIFT, " ", 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> budget.extraCount(ActionType.SWIFT, null)
        );
    }
}
