package io.autoptu.core.runtime;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.ActionEconomyProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAutobattlerActionSpaceProfileTest {
    @Test
    void legalActionProjectionUsesCanonicalBattleProfile() {
        ActionBudget pythonBudget = new ActionBudget(ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY);
        pythonBudget.markAction(ActionType.SWIFT, "used");
        BattleRuntimeState pythonState = state(pythonBudget);

        ActionBudget kairosBudget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);
        kairosBudget.markAction(ActionType.SWIFT, "used");
        BattleRuntimeState kairosState = state(kairosBudget);

        assertEquals(ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY, pythonState.actionEconomyProfile());
        assertEquals(ActionEconomyProfile.KAIROS_2_1_25_1, kairosState.actionEconomyProfile());
        assertFalse(hasMove(choices(pythonState), "swift-candidate"));
        assertTrue(hasMove(choices(kairosState), "swift-candidate"));
    }

    private static BattleRuntimeState state(ActionBudget budget) {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 1),
                30,
                30,
                budget
        );
        return new BattleRuntimeState(
                new MovementGrid(3, 3, Set.of(), Map.of()),
                List.of(actor)
        );
    }

    private static List<BattleChoice> choices(BattleRuntimeState state) {
        MoveOption candidate = new MoveOption(
                "swift-candidate",
                new MoveSpec("Self", "Self", 0, 0, null, null, "Self"),
                ActionType.SWIFT,
                false
        );
        return RuntimeAutobattlerActionSpace.legalChoices(
                state,
                "actor",
                List.of(candidate),
                Set.of(),
                0,
                ignored -> true
        );
    }

    private static boolean hasMove(List<BattleChoice> choices, String moveId) {
        return choices.stream()
                .filter(MoveChoice.class::isInstance)
                .map(MoveChoice.class::cast)
                .anyMatch(choice -> choice.moveId().equals(moveId));
    }
}
