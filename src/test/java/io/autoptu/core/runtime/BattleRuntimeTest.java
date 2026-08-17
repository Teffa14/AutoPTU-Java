package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.model.AccuracyResult;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.DamageDice;
import io.autoptu.core.model.DamageResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
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
    void appliesResolvedMoveDamageAndEmitsAuthoritativeHp() {
        BattleRuntimeState state = stateWithEnemy(35);
        MoveChoice move = combatantMove("thunder-shock");

        AppliedActionResult result = BattleRuntime.applyResolvedMoveOutcome(
                state,
                move,
                "Player",
                new AccuracyResult(true, false, 12, 5),
                damage(12)
        );

        assertEquals(23, state.requireCombatant("enemy").hp());
        assertTrue(!state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
        MoveResolvedEvent event = (MoveResolvedEvent) result.events().getFirst();
        assertEquals(12, event.damage());
        assertEquals(23, event.targetHp());
        assertTrue(event.hit());
    }

    @Test
    void revalidatedResolvedMoveAppliesWhenChoiceIsStillLegal() {
        BattleRuntimeState state = stateWithEnemy(35);
        MoveChoice choice = combatantMove("water-gun");

        AppliedActionResult result = BattleRuntime.applyRevalidatedResolvedMoveOutcome(
                state,
                choice,
                rangedMove("water-gun"),
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new AccuracyResult(true, false, 12, 5),
                damage(12)
        );

        assertEquals(23, state.requireCombatant("enemy").hp());
        assertTrue(!state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
        MoveResolvedEvent event = (MoveResolvedEvent) result.events().getFirst();
        assertEquals(23, event.targetHp());
    }

    @Test
    void staleResolvedMoveIsRejectedBeforeHpOrBudgetMutation() {
        BattleRuntimeState state = stateWithEnemy(35);
        MoveChoice staleChoice = combatantMove("water-gun");
        state.requireCombatant("enemy").moveTo(new GridCoord(5, 5));

        assertThrows(IllegalArgumentException.class, () -> BattleRuntime.applyRevalidatedResolvedMoveOutcome(
                state,
                staleChoice,
                rangedMove("water-gun"),
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new AccuracyResult(true, false, 12, 5),
                damage(12)
        ));

        assertEquals(35, state.requireCombatant("enemy").hp());
        assertTrue(state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void lineOfSightChangeRejectsMoveBeforeMutation() {
        BattleRuntimeState state = stateWithEnemyAt(new GridCoord(3, 1), 35);
        MoveChoice choice = new MoveChoice(
                "actor",
                "water-gun",
                ChoiceTargetMode.COMBATANT,
                "enemy",
                new GridCoord(3, 1),
                ActionType.STANDARD
        );

        assertThrows(IllegalArgumentException.class, () -> BattleRuntime.applyRevalidatedResolvedMoveOutcome(
                state,
                choice,
                rangedMove("water-gun"),
                "Medium",
                "Medium",
                Set.of(new GridCoord(2, 1)),
                "Player",
                new AccuracyResult(true, false, 12, 5),
                damage(12)
        ));

        assertEquals(35, state.requireCombatant("enemy").hp());
        assertTrue(state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void missConsumesActionWithoutMutatingTargetHp() {
        BattleRuntimeState state = stateWithEnemy(35);

        AppliedActionResult result = BattleRuntime.applyResolvedMoveOutcome(
                state,
                combatantMove("thunder-shock"),
                "Player",
                new AccuracyResult(false, false, 2, 5),
                null
        );

        assertEquals(35, state.requireCombatant("enemy").hp());
        MoveResolvedEvent event = (MoveResolvedEvent) result.events().getFirst();
        assertEquals(0, event.damage());
        assertEquals(35, event.targetHp());
        assertTrue(!event.hit());
    }

    @Test
    void secondResolvedMoveIsRejectedBeforeHpMutation() {
        BattleRuntimeState state = stateWithEnemy(35);
        MoveChoice move = combatantMove("tackle");
        BattleRuntime.applyResolvedMoveOutcome(
                state,
                move,
                "Player",
                new AccuracyResult(true, false, 10, 2),
                damage(5)
        );

        assertThrows(IllegalStateException.class, () -> BattleRuntime.applyResolvedMoveOutcome(
                state,
                move,
                "Player",
                new AccuracyResult(true, false, 10, 2),
                damage(5)
        ));
        assertEquals(30, state.requireCombatant("enemy").hp());
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

    private static MoveChoice combatantMove(String moveId) {
        return new MoveChoice(
                "actor",
                moveId,
                ChoiceTargetMode.COMBATANT,
                "enemy",
                new GridCoord(2, 1),
                ActionType.STANDARD
        );
    }

    private static MoveOption rangedMove(String moveId) {
        MoveSpec spec = new MoveSpec("Ranged", "Ranged", 3, 3, null, null, "Ranged");
        return MoveOption.standard(moveId, spec);
    }

    private static DamageResult damage(int amount) {
        return new DamageResult(new DamageDice(1, 6, 0), amount, 0, amount, amount, amount, amount);
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

    private static BattleRuntimeState stateWithEnemy(int enemyHp) {
        return stateWithEnemyAt(new GridCoord(2, 1), enemyHp);
    }

    private static BattleRuntimeState stateWithEnemyAt(GridCoord enemyPosition, int enemyHp) {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                50,
                50,
                new ActionBudget()
        );
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy",
                MovementProfile.walking(enemyPosition, 3),
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
