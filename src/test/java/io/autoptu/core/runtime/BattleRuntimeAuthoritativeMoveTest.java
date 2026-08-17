package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRuntimeAuthoritativeMoveTest {
    @Test
    void runtimeOwnsAccuracyRollDamageRollsAndHpMutation() {
        BattleRuntimeState state = stateWithEnemy(35);

        AppliedActionResult result = BattleRuntime.applyAuthoritativeMove(
                state,
                choice("water-gun"),
                rangedMove("water-gun"),
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new PythonRandom(7),
                input(5)
        );

        MoveResolvedEvent event = (MoveResolvedEvent) result.events().getFirst();
        assertEquals(11, event.roll());
        assertTrue(event.hit());
        assertFalse(event.critical());
        assertEquals(31, event.damage());
        assertEquals(4, event.targetHp());
        assertEquals(4, state.requireCombatant("enemy").hp());
        assertFalse(state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void missConsumesActionWithoutConsumingDamageOrMutatingHp() {
        BattleRuntimeState state = stateWithEnemy(35);

        AppliedActionResult result = BattleRuntime.applyAuthoritativeMove(
                state,
                choice("water-gun"),
                rangedMove("water-gun"),
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new PythonRandom(19),
                input(5)
        );

        MoveResolvedEvent event = (MoveResolvedEvent) result.events().getFirst();
        assertEquals(2, event.roll());
        assertFalse(event.hit());
        assertEquals(0, event.damage());
        assertEquals(35, event.targetHp());
        assertEquals(35, state.requireCombatant("enemy").hp());
        assertFalse(state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void criticalStateComesFromAccuracyAndAddsCriticalDice() {
        BattleRuntimeState state = stateWithEnemy(100);

        AppliedActionResult result = BattleRuntime.applyAuthoritativeMove(
                state,
                choice("water-gun"),
                rangedMove("water-gun"),
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new PythonRandom(5),
                input(5)
        );

        MoveResolvedEvent event = (MoveResolvedEvent) result.events().getFirst();
        assertEquals(20, event.roll());
        assertTrue(event.hit());
        assertTrue(event.critical());
        assertTrue(event.damage() > 0);
    }

    private static MoveResolutionInput input(int moveAc) {
        return new MoveResolutionInput(
                moveAc,
                0,
                0,
                20,
                false,
                false,
                false,
                10,
                20,
                10,
                false,
                1.0,
                List.of()
        );
    }

    private static MoveChoice choice(String moveId) {
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

    private static BattleRuntimeState stateWithEnemy(int enemyHp) {
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
                100,
                new ActionBudget()
        );
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, enemy)
        );
    }
}
