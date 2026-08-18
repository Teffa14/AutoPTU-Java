package io.autoptu.core.runtime;

import io.autoptu.core.event.StatusSkipEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuntimeStatusSkipTest {
    @Test
    void statusSkipConsumesBaseTurnAndEmitsMinecraftFacingEvent() {
        ActionBudget budget = new ActionBudget();
        budget.grantExtra(ActionType.STANDARD, 1);
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                10,
                10,
                budget
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor)
        );

        AppliedActionResult result = BattleRuntime.applyStatusSkip(
                state,
                "actor",
                "Flinch",
                TurnPhase.START,
                "flinched"
        );

        assertFalse(budget.hasActionAvailable(ActionType.STANDARD));
        assertFalse(budget.hasActionAvailable(ActionType.SHIFT));
        assertEquals(1, budget.extraCount(ActionType.STANDARD));
        StatusSkipEvent event = (StatusSkipEvent) result.events().getFirst();
        assertEquals("status_skip|actor|Flinch|start|flinched", event.stableKey());
    }

    @Test
    void alreadySpentBaseActionKeepsItsOriginalDetail() {
        ActionBudget budget = new ActionBudget();
        budget.markAction(ActionType.STANDARD, "Tackle");
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                10,
                10,
                budget
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor)
        );

        BattleRuntime.applyStatusSkip(state, "actor", "Confused", TurnPhase.START, "failed_check");

        assertEquals("Tackle", budget.consumedDetail(ActionType.STANDARD).orElseThrow());
        assertEquals("Status skip", budget.consumedDetail(ActionType.SHIFT).orElseThrow());
    }
}
