package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShiftApplicationTest {
    @Test void appliesLegalShiftAndConsumesBudget() {
        ActionBudget budget = new ActionBudget();
        var result = ShiftApplication.apply("actor", new GridCoord(1, 1), new GridCoord(2, 1),
                Set.of(new GridCoord(2, 1)), budget);
        assertEquals(new GridCoord(2, 1), result.position());
        assertEquals("shift_resolved|actor|1,1|2,1", result.event().stableKey());
        assertFalse(budget.hasActionAvailable(ActionType.SHIFT));
    }

    @Test void rejectsIllegalDestinationWithoutConsumingBudget() {
        ActionBudget budget = new ActionBudget();
        assertThrows(IllegalArgumentException.class, () -> ShiftApplication.apply("actor",
                new GridCoord(1, 1), new GridCoord(3, 1), Set.of(new GridCoord(2, 1)), budget));
        assertTrue(budget.hasActionAvailable(ActionType.SHIFT));
    }

    @Test void rejectsSecondShiftWithoutExtraActionInPythonCompatibilityProfile() {
        ActionBudget budget = new ActionBudget();
        ShiftApplication.apply("actor", new GridCoord(1, 1), new GridCoord(2, 1), Set.of(new GridCoord(2, 1)), budget);
        assertThrows(IllegalStateException.class, () -> ShiftApplication.apply("actor",
                new GridCoord(2, 1), new GridCoord(3, 1), Set.of(new GridCoord(3, 1)), budget));
        assertTrue(budget.hasActionAvailable(ActionType.STANDARD));
    }

    @Test void kairosRejectsSecondMovementAfterRegularMovementWithoutConsumingStandard() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);
        ShiftApplication.apply("actor", new GridCoord(1, 1), new GridCoord(2, 1), Set.of(new GridCoord(2, 1)), budget);

        assertThrows(IllegalStateException.class, () -> ShiftApplication.apply("actor",
                new GridCoord(2, 1), new GridCoord(3, 1), Set.of(new GridCoord(3, 1)), budget));
        assertTrue(budget.hasActionAvailable(ActionType.STANDARD));
        assertTrue(budget.standardConversion().isEmpty());
    }

    @Test void kairosAllowsConvertedMovementWhenRegularShiftWasSpentOnNonMovementEffect() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);
        assertTrue(budget.consume(ActionType.SHIFT, "non-movement-shift"));

        var result = ShiftApplication.apply("actor", new GridCoord(1, 1), new GridCoord(2, 1),
                Set.of(new GridCoord(2, 1)), budget);

        assertEquals(new GridCoord(2, 1), result.position());
        assertEquals(ActionType.SHIFT, budget.standardConversion().orElseThrow());
        assertFalse(budget.hasActionAvailable(ActionType.STANDARD));
    }
}
