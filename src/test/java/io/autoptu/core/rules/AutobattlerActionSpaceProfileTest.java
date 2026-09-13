package io.autoptu.core.rules;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutobattlerActionSpaceProfileTest {
    @Test
    void fullMoveRemainsIndependentUnderPythonButRequiresStandardAndShiftUnderKairos() {
        ActionBudget python = new ActionBudget(ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY);
        ActionBudget kairos = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);
        python.markAction(ActionType.STANDARD, "used");
        kairos.markAction(ActionType.STANDARD, "used");

        assertTrue(moveIds(python, ActionType.FULL).contains("candidate"));
        assertFalse(moveIds(kairos, ActionType.FULL).contains("candidate"));
    }

    @Test
    void kairosCanExposeStandardToSwiftConversionWithoutChangingPythonChoices() {
        ActionBudget python = new ActionBudget(ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY);
        ActionBudget kairos = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);
        python.markAction(ActionType.SWIFT, "used");
        kairos.markAction(ActionType.SWIFT, "used");

        assertFalse(moveIds(python, ActionType.SWIFT).contains("candidate"));
        assertTrue(moveIds(kairos, ActionType.SWIFT).contains("candidate"));
    }

    @Test
    void movementChoicesUseMovementAwareProfileCapacity() {
        ActionBudget python = new ActionBudget(ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY);
        ActionBudget kairos = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);
        python.markAction(ActionType.SHIFT, "non-movement shift");
        kairos.markAction(ActionType.SHIFT, "non-movement shift");

        assertTrue(shiftChoices(python).isEmpty());
        assertFalse(shiftChoices(kairos).isEmpty());
    }

    private static List<String> moveIds(ActionBudget budget, ActionType actionType) {
        MoveOption candidate = new MoveOption(
                "candidate",
                new MoveSpec("Self", "Self", 0, 0, null, null, "Self"),
                actionType,
                false
        );
        List<MoveChoice> choices = AutobattlerActionSpace.legalMoveChoices(
                "actor",
                "Medium",
                grid(),
                new GridCoord(1, 1),
                budget,
                List.of(candidate),
                List.of(),
                Set.of()
        );
        return choices.stream().map(MoveChoice::moveId).toList();
    }

    private static List<?> shiftChoices(ActionBudget budget) {
        return AutobattlerActionSpace.legalShiftChoices(
                "actor",
                grid(),
                MovementProfile.walking(new GridCoord(1, 1), 1),
                budget,
                0,
                ignored -> true
        );
    }

    private static MovementGrid grid() {
        return new MovementGrid(3, 3, Set.of(), Map.of());
    }
}
