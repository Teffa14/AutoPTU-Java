package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.ForcedMovementInstruction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForcedMovementApplicationTest {
    @Test
    void pushMovesTargetAwayFromSourceUntilCollision() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 4, Set.of(new GridCoord(5, 1)), Map.of()),
                List.of(source, target)
        );

        ForcedDisplacementResolution.Result result = ForcedMovementApplication.apply(
                state,
                "source",
                "target",
                new ForcedMovementInstruction(ForcedMovementInstruction.Kind.PUSH, 5)
        );

        assertEquals(new GridCoord(4, 1), result.destination());
        assertEquals(new GridCoord(4, 1), target.position());
        assertEquals(2, result.movedDistance());
    }

    @Test
    void pullMovesTargetTowardSourceAndStopsBeforeOccupiedSourceFootprint() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 4, 1);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 4, Set.of(), Map.of()),
                List.of(source, target)
        );

        ForcedDisplacementResolution.Result result = ForcedMovementApplication.apply(
                state,
                "source",
                "target",
                new ForcedMovementInstruction(ForcedMovementInstruction.Kind.PULL, 5)
        );

        assertEquals(new GridCoord(2, 1), result.destination());
        assertEquals(new GridCoord(2, 1), target.position());
        assertEquals(2, result.movedDistance());
    }

    @Test
    void pushAndPullDoNotSpendOrdinaryShiftBudget() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 3, 1);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 4, Set.of(), Map.of()),
                List.of(source, target)
        );

        ForcedMovementApplication.apply(
                state,
                "source",
                "target",
                new ForcedMovementInstruction(ForcedMovementInstruction.Kind.PUSH, 1)
        );

        assertEquals(0, target.actionBudget().usedCount(io.autoptu.core.rules.ActionType.SHIFT));
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 6),
                20,
                20,
                ActionBudget.fresh()
        );
    }
}
