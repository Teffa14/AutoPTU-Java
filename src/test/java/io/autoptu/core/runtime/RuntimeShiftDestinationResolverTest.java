package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeShiftDestinationResolverTest {
    @Test
    void positionFitMatchesPythonDefaultOccupancyAndTerrainBoundary() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), 2, 10);
        RuntimeCombatantState occupant = combatant("occupant", new GridCoord(2, 1), 0, 0);
        MovementGrid grid = new MovementGrid(
                4,
                4,
                Set.of(new GridCoord(0, 1)),
                Map.of(new GridCoord(1, 0), " Wall ")
        );
        BattleRuntimeState state = new BattleRuntimeState(grid, List.of(actor, occupant));

        assertTrue(RuntimePositionFitResolver.canFit(state, "actor", new GridCoord(1, 1)), "self footprint is excluded");
        assertTrue(RuntimePositionFitResolver.canFit(state, "actor", new GridCoord(2, 1)), "fainted occupants do not block");
        assertFalse(RuntimePositionFitResolver.canFit(state, "actor", new GridCoord(0, 1)), "explicit blockers reject landing");
        assertFalse(RuntimePositionFitResolver.canFit(state, "actor", new GridCoord(1, 0)), "Python blocking tile names reject landing");
    }

    @Test
    void activeConsciousOccupantBlocksShiftDestination() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1), 2, 10);
        RuntimeCombatantState occupant = combatant("occupant", new GridCoord(2, 1), 0, 10);
        BattleRuntimeState state = new BattleRuntimeState(new MovementGrid(5, 5, Set.of(), Map.of()), List.of(actor, occupant));

        Set<GridCoord> legal = RuntimeShiftDestinationResolver.legalShiftTiles(state, "actor", 0);

        assertTrue(legal.contains(new GridCoord(3, 1)), "ordinary Overland reach comes from the runtime movement profile");
        assertFalse(legal.contains(new GridCoord(2, 1)), "active conscious combatants block a landing tile");
    }

    @Test
    void footprintMustFitInsideGrid() {
        RuntimeCombatantState actor = combatant("large", new GridCoord(1, 1), 1, 10);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(3, 3, Set.of(), Map.of()),
                List.of(actor),
                Map.of(),
                Map.of(),
                Map.of("large", new CombatantGeometryState("Large"))
        );

        assertFalse(RuntimePositionFitResolver.canFit(state, "large", new GridCoord(2, 2)));
        assertTrue(RuntimePositionFitResolver.canFit(state, "large", new GridCoord(1, 1)));
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, int overland, int hp) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, overland),
                hp,
                10,
                new ActionBudget()
        );
    }
}
