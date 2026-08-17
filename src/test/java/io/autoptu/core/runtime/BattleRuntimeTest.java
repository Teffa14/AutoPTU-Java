package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRuntimeTest {
    @Test
    void appliesShiftToAuthoritativeStateAndEmitsSemanticEvent() {
        BattleRuntimeState state = state(new MovementGrid(6, 6, Set.of(), Map.of()), 3);

        AppliedActionResult result = BattleRuntime.applyAction(
                state,
                new ShiftChoice("actor", new GridCoord(2, 1)),
                ignored -> true
        );

        RuntimeCombatantState actor = state.requireCombatant("actor");
        assertEquals(new GridCoord(2, 1), actor.position());
        assertEquals(10, actor.hp());
        assertTrue(!actor.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertEquals(1, result.events().size());
        ShiftResolvedEvent event = (ShiftResolvedEvent) result.events().getFirst();
        assertEquals(new GridCoord(1, 1), event.origin());
        assertEquals(new GridCoord(2, 1), event.destination());
    }

    @Test
    void revalidatesMovementInsteadOfTrustingControllerChoice() {
        BattleRuntimeState state = state(
                new MovementGrid(6, 6, Set.of(new GridCoord(2, 1)), Map.of()),
                3
        );

        assertThrows(IllegalArgumentException.class, () -> BattleRuntime.applyAction(
                state,
                new ShiftChoice("actor", new GridCoord(2, 1)),
                ignored -> true
        ));
        assertEquals(new GridCoord(1, 1), state.requireCombatant("actor").position());
        assertTrue(state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.SHIFT));
    }

    @Test
    void secondShiftIsRejectedWithoutAdditionalMutation() {
        BattleRuntimeState state = state(new MovementGrid(6, 6, Set.of(), Map.of()), 3);
        BattleRuntime.applyAction(state, new ShiftChoice("actor", new GridCoord(2, 1)), ignored -> true);

        assertThrows(IllegalStateException.class, () -> BattleRuntime.applyAction(
                state,
                new ShiftChoice("actor", new GridCoord(3, 1)),
                ignored -> true
        ));
        assertEquals(new GridCoord(2, 1), state.requireCombatant("actor").position());
    }

    @Test
    void moveChoiceCannotMutateRuntimeUntilResolverIsPorted() {
        BattleRuntimeState state = state(new MovementGrid(6, 6, Set.of(), Map.of()), 3);
        MoveChoice move = new MoveChoice(
                "actor",
                "tackle",
                ChoiceTargetMode.COMBATANT,
                "enemy",
                new GridCoord(2, 1),
                ActionType.STANDARD
        );

        assertThrows(UnsupportedOperationException.class, () -> BattleRuntime.applyAction(state, move, ignored -> true));
        assertEquals(new GridCoord(1, 1), state.requireCombatant("actor").position());
        assertTrue(state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
    }

    private static BattleRuntimeState state(MovementGrid grid, int overland) {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), overland),
                10,
                10,
                new ActionBudget()
        );
        return new BattleRuntimeState(grid, List.of(actor));
    }
}
