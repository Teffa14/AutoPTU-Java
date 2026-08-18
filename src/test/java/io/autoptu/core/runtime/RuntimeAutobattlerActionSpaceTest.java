package io.autoptu.core.runtime;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAutobattlerActionSpaceTest {
    @Test
    void statusSkipImmediatelyRemovesBaseStandardAndShiftChoices() {
        ActionBudget budget = new ActionBudget();
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), budget);
        BattleRuntimeState state = stateWithEnemy(actor, new GridCoord(2, 1), Map.of());
        List<MoveOption> moves = List.of(
                MoveOption.standard("tackle", move("Melee", 1)),
                new MoveOption("free-focus", move("Self", 0), ActionType.FREE, true)
        );

        List<BattleChoice> before = choices(state, moves);
        assertTrue(before.stream().anyMatch(ShiftChoice.class::isInstance));
        assertTrue(hasMove(before, "tackle"));

        BattleRuntime.applyStatusSkip(state, "actor", "Flinch", TurnPhase.START, "flinched");

        List<BattleChoice> after = choices(state, moves);
        assertFalse(after.stream().anyMatch(ShiftChoice.class::isInstance));
        assertFalse(hasMove(after, "tackle"));
        assertTrue(hasMove(after, "free-focus"));
    }

    @Test
    void legitimateExtraStandardRemainsAvailableAfterStatusSkipLikePythonActionBudget() {
        ActionBudget budget = new ActionBudget();
        budget.grantExtra(ActionType.STANDARD);
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), budget);
        BattleRuntimeState state = stateWithEnemy(actor, new GridCoord(2, 1), Map.of());

        BattleRuntime.applyStatusSkip(state, "actor", "Flinch", TurnPhase.START, "flinched");

        List<BattleChoice> after = choices(state, List.of(MoveOption.standard("tackle", move("Melee", 1))));
        assertFalse(after.stream().anyMatch(ShiftChoice.class::isInstance));
        assertTrue(hasMove(after, "tackle"));
    }

    @Test
    void trainerFeatureBypassPreservesNormalDecisionWindow() {
        ActionBudget budget = new ActionBudget();
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), budget);
        RuntimeCombatantState enemy = combatant("enemy", new GridCoord(2, 1), new ActionBudget());
        StatusSkipFeatureState supreme = new StatusSkipFeatureState(
                "supremeconcentration",
                "Tackle",
                false
        );
        BattleRuntimeState state = new BattleRuntimeState(
                grid(4, 4),
                List.of(actor, enemy),
                Map.of(),
                Map.of("actor", supreme)
        );

        BattleRuntime.applyStatusSkip(state, "actor", "Flinch", TurnPhase.START, "flinched");

        List<BattleChoice> after = choices(state, List.of(MoveOption.standard("tackle", move("Melee", 1))));
        assertTrue(after.stream().anyMatch(ShiftChoice.class::isInstance));
        assertTrue(hasMove(after, "tackle"));
    }

    @Test
    void canonicalSizeAndTargetPositionDriveMeleeFootprintRange() {
        RuntimeCombatantState largeActor = combatant("actor", new GridCoord(0, 0), new ActionBudget());
        RuntimeCombatantState enemy = combatant("enemy", new GridCoord(2, 0), new ActionBudget());
        BattleRuntimeState largeState = new BattleRuntimeState(
                grid(5, 5),
                List.of(largeActor, enemy),
                Map.of(),
                Map.of(),
                Map.of("actor", new CombatantGeometryState("Large"))
        );

        List<BattleChoice> largeChoices = choices(
                largeState,
                List.of(MoveOption.standard("tackle", move("Melee", 1)))
        );
        assertTrue(hasMove(largeChoices, "tackle"));

        RuntimeCombatantState mediumActor = combatant("actor", new GridCoord(0, 0), new ActionBudget());
        RuntimeCombatantState sameEnemy = combatant("enemy", new GridCoord(2, 0), new ActionBudget());
        BattleRuntimeState mediumState = new BattleRuntimeState(grid(5, 5), List.of(mediumActor, sameEnemy));

        List<BattleChoice> mediumChoices = choices(
                mediumState,
                List.of(MoveOption.standard("tackle", move("Melee", 1)))
        );
        assertFalse(hasMove(mediumChoices, "tackle"));
    }

    @Test
    void targetGeometryMustReferenceCanonicalCombatant() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), new ActionBudget());
        BattleRuntimeState state = new BattleRuntimeState(grid(4, 4), List.of(actor));

        assertThrows(IllegalArgumentException.class, () -> RuntimeAutobattlerActionSpace.legalChoices(
                state,
                "actor",
                List.of(MoveOption.standard("tackle", move("Melee", 1))),
                List.of("forged-client-target"),
                Set.of(),
                0,
                ignored -> true
        ));
    }

    private static List<BattleChoice> choices(BattleRuntimeState state, List<MoveOption> moves) {
        return RuntimeAutobattlerActionSpace.legalChoices(
                state,
                "actor",
                moves,
                List.of("enemy"),
                Set.of(),
                0,
                ignored -> true
        );
    }

    private static boolean hasMove(List<BattleChoice> choices, String moveId) {
        return choices.stream()
                .filter(MoveChoice.class::isInstance)
                .map(MoveChoice.class::cast)
                .anyMatch(choice -> choice.moveId().equals(moveId));
    }

    private static BattleRuntimeState stateWithEnemy(
            RuntimeCombatantState actor,
            GridCoord enemyPosition,
            Map<String, CombatantGeometryState> geometry
    ) {
        RuntimeCombatantState enemy = combatant("enemy", enemyPosition, new ActionBudget());
        return new BattleRuntimeState(
                grid(4, 4),
                List.of(actor, enemy),
                Map.of(),
                Map.of(),
                geometry
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, ActionBudget budget) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 1),
                30,
                30,
                budget
        );
    }

    private static MovementGrid grid(int width, int height) {
        return new MovementGrid(width, height, Set.of(), Map.of());
    }

    private static MoveSpec move(String targetKind, Integer range) {
        return new MoveSpec(
                targetKind,
                targetKind,
                range,
                range,
                null,
                null,
                targetKind
        );
    }
}
