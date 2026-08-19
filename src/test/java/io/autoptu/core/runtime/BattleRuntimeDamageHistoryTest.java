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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRuntimeDamageHistoryTest {
    @Test
    void hitRecordsTargetSourceAndActualHpLoss() {
        BattleRuntimeState state = stateWithEnemyHp(5);

        BattleRuntime.applyResolvedMoveOutcome(
                state,
                move("tackle"),
                "Player",
                new AccuracyResult(true, false, 12, 2),
                damage(12)
        );

        assertEquals(0, state.requireCombatant("enemy").hp());
        assertEquals(Set.of("enemy"), state.damageHistory().damageThisRound());
        assertEquals(Set.of("actor"), state.damageHistory().damageTakenFromThisRound().get("enemy"));
        assertEquals(Map.of("enemy", 5), state.damageHistory().damageReceivedThisRound());
    }

    @Test
    void zeroDamageHitStillRecordsPythonExchangeAndZeroAmount() {
        BattleRuntimeState state = stateWithEnemyHp(35);

        BattleRuntime.applyResolvedMoveOutcome(
                state,
                move("status-hit"),
                "Player",
                new AccuracyResult(true, false, 10, 2),
                damage(0)
        );

        assertEquals(Set.of("enemy"), state.damageHistory().damageThisRound());
        assertEquals(Set.of("actor"), state.damageHistory().damageTakenFromThisRound().get("enemy"));
        assertEquals(Map.of("enemy", 0), state.damageHistory().damageReceivedThisRound());
    }

    @Test
    void missDoesNotRecordDamageHistory() {
        BattleRuntimeState state = stateWithEnemyHp(35);

        BattleRuntime.applyResolvedMoveOutcome(
                state,
                move("tackle"),
                "Player",
                new AccuracyResult(false, false, 2, 5),
                null
        );

        assertTrue(state.damageHistory().damageThisRound().isEmpty());
        assertTrue(state.damageHistory().damageTakenFromThisRound().isEmpty());
        assertTrue(state.damageHistory().damageReceivedThisRound().isEmpty());
    }

    @Test
    void defaultRoundControllerRotatesTheSameHistoryWrittenByBattleRuntime() {
        BattleRuntimeState state = stateWithEnemyHp(35);
        BattleRuntime.applyResolvedMoveOutcome(
                state,
                move("tackle"),
                "Player",
                new AccuracyResult(true, false, 10, 2),
                damage(7)
        );

        BattleRoundController rounds = new BattleRoundController(state);
        rounds.startRound();

        assertEquals(state.damageHistory(), rounds.damageHistory());
        assertEquals(Set.of("enemy"), state.damageHistory().damageLastRound());
        assertEquals(Set.of("actor"), state.damageHistory().damageTakenFromLastRound().get("enemy"));
        assertTrue(state.damageHistory().damageReceivedThisRound().isEmpty());
    }

    private static MoveChoice move(String moveId) {
        return new MoveChoice(
                "actor",
                moveId,
                ChoiceTargetMode.COMBATANT,
                "enemy",
                new GridCoord(2, 1),
                ActionType.STANDARD
        );
    }

    private static DamageResult damage(int amount) {
        return new DamageResult(new DamageDice(1, 6, 0), amount, 0, amount, amount, amount, amount);
    }

    private static BattleRuntimeState stateWithEnemyHp(int enemyHp) {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                50,
                50,
                new ActionBudget()
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
