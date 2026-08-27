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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterceptMovementApplicationTest {
    @Test
    void successfulCheckCommitsChosenInterceptPosition() {
        RuntimeCombatantState interceptor = combatant("interceptor", 1, 1);
        BattleRuntimeState state = state(interceptor);
        InterceptCheckResolution.Result check = successCheck();

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
        InterceptCheckResolution.Result check = failedCheck();

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

        InterceptMovementApplication.apply(state, "interceptor", new GridCoord(2, 1), successCheck());

        assertTrue(interceptor.actionBudget().hasActionAvailable(ActionType.SHIFT));
    }

    @Test
    void successfulMeleeInterceptPushesProtectedTargetThenOccupiesItsOriginalAnchor() {
        RuntimeCombatantState interceptor = combatant("interceptor", 1, 1);
        RuntimeCombatantState target = combatant("target", 3, 1);
        BattleRuntimeState state = state(interceptor, target);

        InterceptMovementApplication.MeleeResult result = InterceptMovementApplication.applyMelee(
                state,
                "interceptor",
                "target",
                new GridCoord(2, 1),
                successCheck()
        );

        assertTrue(result.interceptMovement().checkSucceeded());
        assertEquals(new GridCoord(3, 1), result.protectedTargetOrigin());
        assertEquals(1, result.targetPush().requestedDistance());
        assertEquals(1, result.targetPush().movedDistance());
        assertEquals(new GridCoord(4, 1), target.position());
        assertEquals(new GridCoord(3, 1), interceptor.position());
        assertEquals(new GridCoord(3, 1), result.interceptorDestination());
        assertEquals(new GridCoord(4, 1), result.protectedTargetDestination());
        assertTrue(interceptor.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertTrue(target.actionBudget().hasActionAvailable(ActionType.SHIFT));
    }

    @Test
    void blockedMeleePushStillCommitsPythonFinalInterceptorAnchor() {
        RuntimeCombatantState interceptor = combatant("interceptor", 1, 1);
        RuntimeCombatantState target = combatant("target", 3, 1);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(new GridCoord(4, 1)), Map.of()),
                List.of(interceptor, target)
        );

        InterceptMovementApplication.MeleeResult result = InterceptMovementApplication.applyMelee(
                state,
                "interceptor",
                "target",
                new GridCoord(2, 1),
                successCheck()
        );

        assertEquals(0, result.targetPush().movedDistance());
        assertTrue(result.targetPush().stoppedEarly());
        assertEquals(new GridCoord(3, 1), target.position());
        assertEquals(new GridCoord(3, 1), interceptor.position());
    }

    @Test
    void failedMeleeInterceptChangesNeitherCombatant() {
        RuntimeCombatantState interceptor = combatant("interceptor", 1, 1);
        RuntimeCombatantState target = combatant("target", 3, 1);
        BattleRuntimeState state = state(interceptor, target);

        InterceptMovementApplication.MeleeResult result = InterceptMovementApplication.applyMelee(
                state,
                "interceptor",
                "target",
                new GridCoord(2, 1),
                failedCheck()
        );

        assertFalse(result.interceptMovement().checkSucceeded());
        assertNull(result.targetPush());
        assertEquals(new GridCoord(1, 1), interceptor.position());
        assertEquals(new GridCoord(3, 1), target.position());
    }

    private static InterceptCheckResolution.Result successCheck() {
        return InterceptCheckResolution.resolve(
                new InterceptCheckResolution.Input(20, 2, 0, 0, 0, 0, false)
        );
    }

    private static InterceptCheckResolution.Result failedCheck() {
        return InterceptCheckResolution.resolve(
                new InterceptCheckResolution.Input(1, 2, 0, 0, 0, 0, false)
        );
    }

    private static BattleRuntimeState state(RuntimeCombatantState... combatants) {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(combatants)
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
