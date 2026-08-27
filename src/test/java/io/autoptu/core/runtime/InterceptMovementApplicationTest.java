package io.autoptu.core.runtime;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterceptMovementApplicationTest {
    @Test
    void successfulCheckCommitsChosenInterceptPosition() {
        RuntimeCombatantState interceptor = combatant("interceptor", 1, 1);
        BattleRuntimeState state = state(interceptor);
        InterceptCheckResolution.Result check = InterceptCheckResolution.resolve(
                new InterceptCheckResolution.Input(20, 2, 0, 0, 0, 0, false)
        );

        InterceptMovementApplication.Result result = InterceptMovementApplication.apply(
                state, "interceptor", new GridCoord(3, 1), check
        );

        assertTrue(result.checkSucceeded());
        assertTrue(result.moved());
        assertEquals(new GridCoord(1, 1), result.origin());
        assertEquals(new GridCoord(3, 1), result.destination());
        assertEquals(new GridCoord(3, 1), interceptor.position());
    }

    @Test
    void failedCheckLeavesInterceptorAtOrigin() {
        RuntimeCombatantState interceptor = combatant("interceptor", 1, 1);
        BattleRuntimeState state = state(interceptor);
        InterceptCheckResolution.Result check = InterceptCheckResolution.resolve(
                new InterceptCheckResolution.Input(1, 2, 0, 0, 0, 0, false)
        );

        InterceptMovementApplication.Result result = InterceptMovementApplication.apply(
                state, "interceptor", new GridCoord(3, 1), check
        );

        assertFalse(result.checkSucceeded());
        assertFalse(result.moved());
        assertEquals(new GridCoord(1, 1), result.destination());
        assertEquals(new GridCoord(1, 1), interceptor.position());
    }

    @Test
    void reactionMovementDoesNotSpendOrdinaryShift() {
        RuntimeCombatantState interceptor = combatant("interceptor", 1, 1);
        BattleRuntimeState state = state(interceptor);
        InterceptCheckResolution.Result check = InterceptCheckResolution.resolve(
                new InterceptCheckResolution.Input(20, 1, 0, 0, 0, 0, false)
        );

        InterceptMovementApplication.apply(state, "interceptor", new GridCoord(2, 1), check);

        assertTrue(interceptor.actionBudget().hasActionAvailable(ActionType.SHIFT));
    }

    private static BattleRuntimeState state(RuntimeCombatantState combatant) {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(combatant)
        );
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 6),
                20,
                20,
                new ActionBudget()
        );
    }
}
