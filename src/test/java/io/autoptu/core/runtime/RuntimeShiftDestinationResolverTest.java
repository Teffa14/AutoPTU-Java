package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeShiftDestinationResolverTest {
    @Test
    void pinnedPythonContractRemainsWiredIntoJavaGate() throws IOException {
        String oraclePath = System.getProperty("autoptu.shift.positionFit.oracle", "").strip();
        if (oraclePath.isEmpty()) {
            return;
        }
        Map<String, String> contract = readContract(Path.of(oraclePath));
        assertEquals("actor.footprint_tiles(destination)", contract.get("position_fit.footprint"));
        assertEquals("reject", contract.get("position_fit.out_of_bounds"));
        assertEquals("wall,blocker,blocking,void", contract.get("position_fit.blocking_tile_types"));
        assertEquals("true", contract.get("position_fit.default.exclude_self"));
        assertEquals("true", contract.get("position_fit.default.active_only"));
        assertEquals("true", contract.get("position_fit.default.conscious_only"));
        assertEquals("true", contract.get("position_fit.default.block_on_terrain"));
        assertEquals("battle_state", contract.get("shift.position_fit_source"));
        assertEquals("actor", contract.get("shift.profile_source"));
    }

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

    private static Map<String, String> readContract(Path path) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(path);
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split("\\t", 2);
            if (parts.length == 2) {
                values.put(parts[0], parts[1]);
            }
        }
        return values;
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
