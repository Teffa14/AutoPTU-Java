package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.ActionType;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeForcedMovementContentPreventionTest {
    @Test
    void insectoidUtilityAndWallclimberPreventPushAfterHit() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption move = move("ram", List.of("push 2"));
        BattleRuntimeState state = state(source, target, move);
        CombatantRuleContent content = new CombatantRuleContent(
                List.of("Wallclimber"), null, "trainer", Map.of(),
                List.of("Insectoid Utility"), List.of()
        );

        assertTrue(RuntimePostHitForcedMovementApplication.apply(
                state, choice(source, target, move), true, content
        ).isEmpty());
        assertEquals(new GridCoord(2, 1), target.position());
    }

    @Test
    void incompleteCompositeGuardDoesNotPreventPush() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption move = move("ram", List.of("push"));
        BattleRuntimeState state = state(source, target, move);
        CombatantRuleContent content = new CombatantRuleContent(
                List.of(), null, "trainer", Map.of(),
                List.of("Insectoid Utility"), List.of()
        );

        assertTrue(RuntimePostHitForcedMovementApplication.apply(
                state, choice(source, target, move), true, content
        ).isPresent());
        assertEquals(new GridCoord(3, 1), target.position());
    }

    private static BattleRuntimeState state(
            RuntimeCombatantState source,
            RuntimeCombatantState target,
            MoveOption move
    ) {
        return new BattleRuntimeState(
                new MovementGrid(8, 4, Set.of(), Map.of()),
                List.of(source, target),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of("source", List.of(move))
        );
    }

    private static MoveChoice choice(RuntimeCombatantState source, RuntimeCombatantState target, MoveOption move) {
        return new MoveChoice(
                source.combatantId(), move.moveId(), ChoiceTargetMode.COMBATANT,
                target.combatantId(), target.position(), move.actionType()
        );
    }

    private static MoveOption move(String moveId, List<String> keywords) {
        return new MoveOption(
                moveId,
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee", keywords, ""),
                ActionType.STANDARD,
                true
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
