package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRuntimeAuthoritativeMoveTest {
    @Test
    void runtimeOwnsAccuracyDamageAndHpMutation() {
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
        assertTrue(event.hit());
        assertFalse(event.crit());
        assertTrue(event.damage() > 0);
        assertEquals(Math.max(0, 35 - event.damage()), event.targetHp());
        assertEquals(event.targetHp(), state.requireCombatant("enemy").hp());
        assertFalse(state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void missConsumesActionWithoutMutatingHp() {
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
        assertFalse(event.hit());
        assertEquals(0, event.damage());
        assertEquals(35, event.targetHp());
        assertEquals(35, state.requireCombatant("enemy").hp());
        assertFalse(state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void criticalStateComesFromAccuracy() {
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
        assertTrue(event.hit());
        assertTrue(event.crit());
        assertTrue(event.damage() > 0);
    }

    @Test
    void hitAppliesCanonicalPushAfterResolvedOutcome() {
        MoveOption move = pushMove("ram");
        BattleRuntimeState state = stateWithEnemyAndMove(35, move);

        AppliedActionResult result = BattleRuntime.applyAuthoritativeMove(
                state,
                choice("ram"),
                move,
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new PythonRandom(7),
                input(5)
        );

        MoveResolvedEvent event = (MoveResolvedEvent) result.events().getFirst();
        assertTrue(event.hit());
        assertTrue(event.damage() > 0);
        assertEquals(new GridCoord(4, 1), state.requireCombatant("enemy").position());
        assertFalse(state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void defenderRuleContentPreventsCanonicalPushThroughAuthoritativeMove() {
        MoveOption move = pushMove("ram");
        BattleRuntimeState state = stateWithEnemyAndMove(35, move);
        BattleRuntimeDependencies dependencies = dependenciesFor(
                "enemy", insectoidWallclimberContent()
        );

        AppliedActionResult result = BattleRuntime.applyAuthoritativeMove(
                state,
                choice("ram"),
                move,
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new PythonRandom(7),
                input(5),
                dependencies
        );

        MoveResolvedEvent event = assertInstanceOf(MoveResolvedEvent.class, result.events().getFirst());
        TrainerFeatureEvent prevention = assertInstanceOf(TrainerFeatureEvent.class, result.events().get(1));
        assertEquals(2, result.events().size());
        assertTrue(event.hit());
        assertTrue(event.damage() > 0);
        assertEquals("enemy", prevention.actorId());
        assertEquals("Insectoid Utility", prevention.feature());
        assertEquals("forced_movement_block", prevention.effect());
        assertEquals("trainer", prevention.trainer());
        assertEquals(event.targetHp(), prevention.targetHp());
        assertEquals(new GridCoord(2, 1), state.requireCombatant("enemy").position());
        assertFalse(state.requireCombatant("actor").actionBudget().hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void attackerRuleContentDoesNotPreventDefenderCanonicalPush() {
        MoveOption move = pushMove("ram");
        BattleRuntimeState state = stateWithEnemyAndMove(35, move);
        BattleRuntimeDependencies dependencies = dependenciesFor(
                "actor", insectoidWallclimberContent()
        );

        BattleRuntime.applyAuthoritativeMove(
                state,
                choice("ram"),
                move,
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new PythonRandom(7),
                input(5),
                dependencies
        );

        assertEquals(new GridCoord(4, 1), state.requireCombatant("enemy").position());
    }

    @Test
    void emptyDependenciesPreserveCanonicalPushThroughAuthoritativeMove() {
        MoveOption move = pushMove("ram");
        BattleRuntimeState state = stateWithEnemyAndMove(35, move);

        BattleRuntime.applyAuthoritativeMove(
                state,
                choice("ram"),
                move,
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new PythonRandom(7),
                input(5),
                BattleRuntimeDependencies.empty()
        );

        assertEquals(new GridCoord(4, 1), state.requireCombatant("enemy").position());
    }

    @Test
    void missDoesNotApplyCanonicalPush() {
        MoveOption move = pushMove("ram");
        BattleRuntimeState state = stateWithEnemyAndMove(35, move);

        AppliedActionResult result = BattleRuntime.applyAuthoritativeMove(
                state,
                choice("ram"),
                move,
                "Medium",
                "Medium",
                Set.of(),
                "Player",
                new PythonRandom(19),
                input(5)
        );

        MoveResolvedEvent event = (MoveResolvedEvent) result.events().getFirst();
        assertFalse(event.hit());
        assertEquals(new GridCoord(2, 1), state.requireCombatant("enemy").position());
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

    private static MoveOption pushMove(String moveId) {
        MoveSpec spec = new MoveSpec(
                "Melee", "Melee", 1, 1, null, null, "Melee",
                List.of("push 2"), "Push the target 2 meters."
        );
        return new MoveOption(moveId, spec, ActionType.STANDARD, true);
    }

    private static BattleRuntimeDependencies dependenciesFor(String combatantId, CombatantRuleContent content) {
        return new BattleRuntimeDependencies(
                new CombatantRuleContentRegistry(Map.of(combatantId, content))
        );
    }

    private static CombatantRuleContent insectoidWallclimberContent() {
        return new CombatantRuleContent(
                List.of("Wallclimber"), null, "trainer", Map.of(),
                List.of("Insectoid Utility"), List.of()
        );
    }

    private static BattleRuntimeState stateWithEnemy(int enemyHp) {
        RuntimeCombatantState actor = actor();
        RuntimeCombatantState enemy = enemy(enemyHp);
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, enemy)
        );
    }

    private static BattleRuntimeState stateWithEnemyAndMove(int enemyHp, MoveOption move) {
        RuntimeCombatantState actor = actor();
        RuntimeCombatantState enemy = enemy(enemyHp);
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, enemy),
                Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("actor", List.of(move))
        );
    }

    private static RuntimeCombatantState actor() {
        return new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                50,
                50,
                new ActionBudget()
        );
    }

    private static RuntimeCombatantState enemy(int enemyHp) {
        return new RuntimeCombatantState(
                "enemy",
                MovementProfile.walking(new GridCoord(2, 1), 3),
                enemyHp,
                100,
                new ActionBudget()
        );
    }
}
