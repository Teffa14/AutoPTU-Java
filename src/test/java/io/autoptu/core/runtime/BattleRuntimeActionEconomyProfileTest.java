package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.model.AccuracyResult;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.DamageDice;
import io.autoptu.core.model.DamageResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.ActionEconomyProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRuntimeActionEconomyProfileTest {
    @Test
    void kairosRuntimeUsesStandardToPayForSecondSwiftMove() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);
        budget.markAction(ActionType.SWIFT, "first-swift");
        BattleRuntimeState state = state(budget, 30);

        BattleRuntime.applyResolvedMoveOutcome(
                state,
                move(ActionType.SWIFT, "second-swift"),
                "Player",
                hit(),
                damage(5)
        );

        assertEquals(25, state.requireCombatant("enemy").hp());
        assertEquals(ActionType.SWIFT, budget.standardConversion().orElseThrow());
        assertTrue(!budget.hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void pythonCompatibilityStillRejectsSecondSwiftMoveBeforeMutation() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY);
        budget.markAction(ActionType.SWIFT, "first-swift");
        BattleRuntimeState state = state(budget, 30);

        assertThrows(IllegalStateException.class, () -> BattleRuntime.applyResolvedMoveOutcome(
                state,
                move(ActionType.SWIFT, "second-swift"),
                "Player",
                hit(),
                damage(5)
        ));

        assertEquals(30, state.requireCombatant("enemy").hp());
        assertTrue(budget.hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void kairosRuntimeRejectsFullMoveWhenStandardWasAlreadySpent() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);
        budget.markAction(ActionType.STANDARD, "first-standard");
        BattleRuntimeState state = state(budget, 30);

        assertThrows(IllegalStateException.class, () -> BattleRuntime.applyResolvedMoveOutcome(
                state,
                move(ActionType.FULL, "full-move"),
                "Player",
                hit(),
                damage(5)
        ));

        assertEquals(30, state.requireCombatant("enemy").hp());
        assertTrue(budget.hasActionAvailable(ActionType.SHIFT));
    }

    @Test
    void pythonCompatibilityKeepsFullIndependentAfterStandardWasSpent() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY);
        budget.markAction(ActionType.STANDARD, "first-standard");
        BattleRuntimeState state = state(budget, 30);

        BattleRuntime.applyResolvedMoveOutcome(
                state,
                move(ActionType.FULL, "full-move"),
                "Player",
                hit(),
                damage(5)
        );

        assertEquals(25, state.requireCombatant("enemy").hp());
        assertTrue(!budget.hasActionAvailable(ActionType.FULL));
    }

    private static MoveChoice move(ActionType actionType, String moveId) {
        return new MoveChoice(
                "actor",
                moveId,
                ChoiceTargetMode.COMBATANT,
                "enemy",
                new GridCoord(2, 1),
                actionType
        );
    }

    private static AccuracyResult hit() {
        return new AccuracyResult(true, false, 12, 5);
    }

    private static DamageResult damage(int amount) {
        return new DamageResult(new DamageDice(1, 6, 0), amount, 0, amount, amount, amount, amount);
    }

    private static BattleRuntimeState state(ActionBudget actorBudget, int enemyHp) {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                50,
                50,
                actorBudget
        );
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy",
                MovementProfile.walking(new GridCoord(2, 1), 3),
                enemyHp,
                50,
                new ActionBudget()
        );
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, enemy)
        );
    }
}
