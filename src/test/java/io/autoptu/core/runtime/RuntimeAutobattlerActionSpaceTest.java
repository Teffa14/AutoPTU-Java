package io.autoptu.core.runtime;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.action.TargetCandidate;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAutobattlerActionSpaceTest {
    @Test
    void statusSkipImmediatelyRemovesBaseStandardAndShiftChoices() {
        ActionBudget budget = new ActionBudget();
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), budget);
        BattleRuntimeState state = new BattleRuntimeState(grid(), List.of(actor));
        List<MoveOption> moves = List.of(
                MoveOption.standard("tackle", move("Melee", 1)),
                new MoveOption("free-focus", move("Self", 0), ActionType.FREE, true)
        );

        List<BattleChoice> before = choices(state, moves);
        assertTrue(before.stream().anyMatch(ShiftChoice.class::isInstance));
        assertTrue(before.stream().filter(MoveChoice.class::isInstance)
                .map(MoveChoice.class::cast)
                .anyMatch(choice -> choice.moveId().equals("tackle")));

        BattleRuntime.applyStatusSkip(state, "actor", "Flinch", TurnPhase.START, "flinched");

        List<BattleChoice> after = choices(state, moves);
        assertFalse(after.stream().anyMatch(ShiftChoice.class::isInstance));
        assertFalse(after.stream().filter(MoveChoice.class::isInstance)
                .map(MoveChoice.class::cast)
                .anyMatch(choice -> choice.moveId().equals("tackle")));
        assertTrue(after.stream().filter(MoveChoice.class::isInstance)
                .map(MoveChoice.class::cast)
                .anyMatch(choice -> choice.moveId().equals("free-focus")));
    }

    @Test
    void legitimateExtraStandardRemainsAvailableAfterStatusSkipLikePythonActionBudget() {
        ActionBudget budget = new ActionBudget();
        budget.grantExtra(ActionType.STANDARD);
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), budget);
        BattleRuntimeState state = new BattleRuntimeState(grid(), List.of(actor));

        BattleRuntime.applyStatusSkip(state, "actor", "Flinch", TurnPhase.START, "flinched");

        List<BattleChoice> after = choices(state, List.of(MoveOption.standard("tackle", move("Melee", 1))));
        assertFalse(after.stream().anyMatch(ShiftChoice.class::isInstance));
        assertTrue(after.stream().filter(MoveChoice.class::isInstance)
                .map(MoveChoice.class::cast)
                .anyMatch(choice -> choice.moveId().equals("tackle")));
    }

    @Test
    void trainerFeatureBypassPreservesNormalDecisionWindow() {
        ActionBudget budget = new ActionBudget();
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), budget);
        StatusSkipFeatureState supreme = new StatusSkipFeatureState(
                "supremeconcentration",
                "Tackle",
                false
        );
        BattleRuntimeState state = new BattleRuntimeState(
                grid(),
                List.of(actor),
                Map.of(),
                Map.of("actor", supreme)
        );

        BattleRuntime.applyStatusSkip(state, "actor", "Flinch", TurnPhase.START, "flinched");

        List<BattleChoice> after = choices(state, List.of(MoveOption.standard("tackle", move("Melee", 1))));
        assertTrue(after.stream().anyMatch(ShiftChoice.class::isInstance));
        assertTrue(after.stream().filter(MoveChoice.class::isInstance)
                .map(MoveChoice.class::cast)
                .anyMatch(choice -> choice.moveId().equals("tackle")));
    }

    private static List<BattleChoice> choices(BattleRuntimeState state, List<MoveOption> moves) {
        return RuntimeAutobattlerActionSpace.legalChoices(
                state,
                "actor",
                "Medium",
                moves,
                List.of(new TargetCandidate("enemy", new GridCoord(2, 1), "Medium")),
                Set.of(),
                0,
                ignored -> true
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

    private static MovementGrid grid() {
        return new MovementGrid(4, 4, Set.of(), Map.of());
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
