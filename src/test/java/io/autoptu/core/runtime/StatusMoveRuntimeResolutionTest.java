package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusMoveRuntimeResolutionTest {
    @Test
    void statusProfileIsCanonicalMetadataAndHitDealsNoDamage() {
        BattleRuntimeState state = stateWithEnemy(35);
        MoveOption move = statusMove("growl", "Scene x1");

        AppliedActionResult result = StatusMoveRuntimeResolution.applyAuthoritativeCombatantStatusMove(
                state,
                choice("growl"),
                move,
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new PythonRandom(7),
                input(1)
        );

        MoveResolvedEvent event = (MoveResolvedEvent) result.events().getFirst();
        assertTrue(event.hit());
        assertFalse(event.crit());
        assertEquals(0, event.damage());
        assertEquals(35, event.targetHp());
        assertEquals(35, state.requireCombatant("enemy").hp());
        assertTrue(state.damageHistory().damageThisRound().isEmpty());
        assertTrue(state.damageHistory().damageReceivedThisRound().isEmpty());
        assertFalse(state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(1, state.requireCombatant("actor").moveFrequencyUsage().battleUses("growl"));
        assertEquals("status", move.requireCombatProfile().damageCategory());
    }

    @Test
    void statusMissStillSpendsDeclarationWithoutDamageHistory() {
        BattleRuntimeState state = stateWithEnemy(35);
        MoveOption move = statusMove("growl", "Scene x1");

        AppliedActionResult result = StatusMoveRuntimeResolution.applyAuthoritativeCombatantStatusMove(
                state,
                choice("growl"),
                move,
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new PythonRandom(7),
                input(99)
        );

        MoveResolvedEvent event = (MoveResolvedEvent) result.events().getFirst();
        assertFalse(event.hit());
        assertFalse(event.crit());
        assertEquals(0, event.damage());
        assertEquals(35, state.requireCombatant("enemy").hp());
        assertTrue(state.damageHistory().damageThisRound().isEmpty());
        assertEquals(1, state.requireCombatant("actor").moveFrequencyUsage().battleUses("growl"));
    }

    @Test
    void damagingProfileCannotEnterStatusRuntimeBoundary() {
        BattleRuntimeState state = stateWithEnemy(35);
        MoveSpec spec = new MoveSpec("Ranged", "Ranged", 3, 3, null, null, "Ranged");
        MoveOption move = new MoveOption(
                "water-gun",
                spec,
                ActionType.STANDARD,
                true,
                new MoveCombatProfile(2, 5, 20, "Special", "Water"),
                null
        );

        assertThrows(IllegalArgumentException.class, () ->
                StatusMoveRuntimeResolution.applyAuthoritativeCombatantStatusMove(
                        state, choice("water-gun"), move, "Medium", "Medium", Set.of(),
                        "Player", new PythonRandom(7), input(1)));
        assertTrue(state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertEquals(35, state.requireCombatant("enemy").hp());
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
                0,
                0,
                0,
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

    private static MoveOption statusMove(String moveId, String frequency) {
        MoveSpec spec = new MoveSpec("Ranged", "Ranged", 3, 3, null, null, "Ranged");
        return new MoveOption(
                moveId,
                spec,
                ActionType.STANDARD,
                true,
                new MoveCombatProfile(2, 0, 20, "Status", "Normal"),
                frequency
        );
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
