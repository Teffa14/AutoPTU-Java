package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitiativeProgressStateTest {
    @Test
    void lifecycleOwnsOrderAndCursorAndRoundStartResetsCursor() {
        BattleRuntimeState state = state();
        BattleRoundController controller = new BattleRoundController(state);

        controller.replaceInitiativeOrder(List.of("target", "actor"));
        controller.setInitiativeCursor(0);

        assertEquals(List.of("target", "actor"), state.initiativeProgress().orderedActorIds());
        assertEquals(0, state.initiativeProgress().cursor());
        assertFalse(state.initiativeProgress().cursorPassed("target"));

        controller.setInitiativeCursor(1);
        assertTrue(state.initiativeProgress().cursorPassed("target"));
        assertFalse(state.initiativeProgress().cursorPassed("actor"));

        controller.startRound();
        assertEquals(-1, state.initiativeProgress().cursor());
        assertEquals(List.of("target", "actor"), state.initiativeProgress().orderedActorIds());
    }

    @Test
    void initiativeOrderRejectsDuplicateIdsAndCursorBelowPythonSentinel() {
        BattleRoundController controller = new BattleRoundController(state());
        assertThrows(IllegalArgumentException.class,
                () -> controller.replaceInitiativeOrder(List.of("actor", "actor")));
        assertThrows(IllegalArgumentException.class,
                () -> controller.setInitiativeCursor(-2));
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 4),
                20, 20, new ActionBudget());
        RuntimeCombatantState target = new RuntimeCombatantState(
                "target", MovementProfile.walking(new GridCoord(1, 0), 4),
                20, 20, new ActionBudget());
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor, target)
        );
    }
}
