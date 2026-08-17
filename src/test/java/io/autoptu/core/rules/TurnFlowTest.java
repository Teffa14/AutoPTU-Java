package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.TurnPhase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnFlowTest {
    @Test
    void actionTypeValuesMatchPython() {
        assertEquals("standard", ActionType.STANDARD.value());
        assertEquals("shift", ActionType.SHIFT.value());
        assertEquals("swift", ActionType.SWIFT.value());
        assertEquals("full", ActionType.FULL.value());
        assertEquals("free", ActionType.FREE.value());
    }

    @Test
    void phaseSequenceMatchesPythonControllerOrder() {
        assertEquals(
                List.of(TurnPhase.START, TurnPhase.COMMAND, TurnPhase.ACTION, TurnPhase.END),
                PhaseSequence.ORDER
        );
        assertEquals(TurnPhase.COMMAND, PhaseSequence.next(TurnPhase.START));
        assertEquals(TurnPhase.ACTION, PhaseSequence.next(TurnPhase.COMMAND));
        assertEquals(TurnPhase.END, PhaseSequence.next(TurnPhase.ACTION));
        assertEquals(TurnPhase.END, PhaseSequence.next(TurnPhase.END));
        assertEquals(TurnPhase.COMMAND, PhaseSequence.next(null));
    }

    @Test
    void actionBudgetMirrorsPythonMarkAndAvailabilitySemantics() {
        ActionBudget budget = new ActionBudget();
        assertTrue(budget.hasActionAvailable(ActionType.STANDARD));
        budget.markAction(ActionType.STANDARD, "Thunderbolt");
        assertFalse(budget.hasActionAvailable(ActionType.STANDARD));
        assertEquals("Thunderbolt", budget.consumedDetail(ActionType.STANDARD).orElseThrow());
        assertTrue(budget.hasActionAvailable(ActionType.SHIFT));
    }

    @Test
    void freeActionsDoNotConsumeTheBudget() {
        ActionBudget budget = new ActionBudget();
        assertTrue(budget.consume(ActionType.FREE, "Free effect"));
        assertTrue(budget.consume(ActionType.FREE, "Second free effect"));
        assertTrue(budget.hasActionAvailable(ActionType.FREE));
    }

    @Test
    void extraActionIsUsedOnlyAfterBaseBucketIsConsumed() {
        ActionBudget budget = new ActionBudget();
        budget.grantExtra(ActionType.STANDARD, 2);

        assertTrue(budget.consume(ActionType.STANDARD, "First"));
        assertEquals(2, budget.extraCount(ActionType.STANDARD));
        assertTrue(budget.consume(ActionType.STANDARD, "Second"));
        assertEquals(1, budget.extraCount(ActionType.STANDARD));
        assertTrue(budget.consume(ActionType.STANDARD, "Third"));
        assertEquals(0, budget.extraCount(ActionType.STANDARD));
        assertFalse(budget.consume(ActionType.STANDARD, "Fourth"));
    }

    @Test
    void resetConsumedActionsDoesNotInventCrossBucketCoupling() {
        ActionBudget budget = new ActionBudget();
        budget.markAction(ActionType.FULL, "Intercept");
        assertFalse(budget.hasActionAvailable(ActionType.FULL));
        assertTrue(budget.hasActionAvailable(ActionType.STANDARD));
        assertTrue(budget.hasActionAvailable(ActionType.SHIFT));
        budget.resetConsumedActions();
        assertTrue(budget.hasActionAvailable(ActionType.FULL));
    }
}
