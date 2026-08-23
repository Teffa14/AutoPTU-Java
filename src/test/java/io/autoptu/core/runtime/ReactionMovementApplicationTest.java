package io.autoptu.core.runtime;

import io.autoptu.core.event.ShiftResolvedEvent;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReactionMovementApplicationTest {
    @Test
    void derivesReachabilityFromRuntimeAndCommitsFarthestSafeDestinationWithoutSpendingShift() {
        ActionBudget budget = new ActionBudget();
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "defender",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                20,
                20,
                budget
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor)
        );

        AppliedActionResult result = ReactionMovementApplication.escapeThreatenedArea(
                state,
                "defender",
                Set.of(new GridCoord(1, 1), new GridCoord(2, 1)),
                null
        );

        assertEquals(new GridCoord(1, 4), actor.position());
        assertTrue(budget.hasActionAvailable(ActionType.SHIFT));
        assertEquals(1, result.events().size());
        ShiftResolvedEvent event = (ShiftResolvedEvent) result.events().getFirst();
        assertEquals(new GridCoord(1, 1), event.origin());
        assertEquals(new GridCoord(1, 4), event.destination());
    }

    @Test
    void distanceCapAndCollisionProjectionAreAppliedBeforeMutation() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "defender",
                MovementProfile.walking(new GridCoord(2, 2), 4),
                20,
                20,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(7, 7, Set.of(), Map.of()),
                List.of(actor)
        );

        AppliedActionResult result = ReactionMovementApplication.escapeThreatenedArea(
                state,
                "defender",
                Set.of(new GridCoord(2, 2), new GridCoord(3, 2)),
                1,
                coord -> !coord.equals(new GridCoord(1, 2))
        );

        assertEquals(new GridCoord(2, 1), actor.position());
        assertEquals(1, result.events().size());
    }

    @Test
    void noSafeDestinationLeavesCanonicalStateAndActionBudgetUntouched() {
        ActionBudget budget = new ActionBudget();
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "defender",
                MovementProfile.walking(new GridCoord(1, 1), 1),
                20,
                20,
                budget
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(3, 3, Set.of(), Map.of()),
                List.of(actor)
        );

        AppliedActionResult result = ReactionMovementApplication.escapeThreatenedArea(
                state,
                "defender",
                Set.of(
                        new GridCoord(1, 1),
                        new GridCoord(0, 1),
                        new GridCoord(2, 1),
                        new GridCoord(1, 0),
                        new GridCoord(1, 2)
                ),
                null
        );

        assertEquals(new GridCoord(1, 1), actor.position());
        assertTrue(result.events().isEmpty());
        assertTrue(budget.hasActionAvailable(ActionType.SHIFT));
    }
}
