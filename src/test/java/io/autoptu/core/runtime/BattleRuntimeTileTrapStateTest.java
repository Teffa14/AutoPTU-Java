package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRuntimeTileTrapStateTest {
    @Test
    void battleStateOwnsDeterministicTrapMaterializationAndConsumption() {
        BattleRuntimeState state = state();
        GridCoord firstTile = new GridCoord(2, 2);
        GridCoord secondTile = new GridCoord(3, 2);
        var abrasion = trap("abrasion_trap", 2, "source-a", "red", "Abrasion Trap");
        var sticky = trap("sticky_trap", 1, "source-b", "blue", "Sticky Trap");
        var second = trap("second_tile", 1, "source-c", "green", "Second Tile");

        state.replaceTileTrapsFromRuntime(firstTile, List.of(abrasion, sticky));
        state.putTileTrapFromRuntime(secondTile, second);

        assertEquals(List.of(firstTile, secondTile), List.copyOf(state.tileTraps().keySet()));
        assertEquals(List.of(abrasion, sticky), state.tileTrapsAt(firstTile));
        assertEquals(List.of(second), state.tileTrapsAt(secondTile));

        assertTrue(state.consumeTileTrapFromRuntime(firstTile, " Abrasion_Trap "));
        assertEquals(List.of(sticky), state.tileTrapsAt(firstTile));
        assertFalse(state.consumeTileTrapFromRuntime(firstTile, "abrasion_trap"));
    }

    @Test
    void publicSnapshotsCannotMutateAuthoritativeTrapState() {
        BattleRuntimeState state = state();
        GridCoord coordinate = new GridCoord(1, 1);
        var trap = trap("sticky_trap", 1, "source", "red", "Sticky Trap");
        state.putTileTrapFromRuntime(coordinate, trap);

        Map<GridCoord, List<TileEntryTrapResolution.TrapLayer>> snapshot = state.tileTraps();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.get(coordinate).clear());
        assertEquals(List.of(trap), state.tileTrapsAt(coordinate));
    }

    private static BattleRuntimeState state() {
        return new BattleRuntimeState(
                new MovementGrid(5, 5, Set.of(), Map.of()),
                List.of()
        );
    }

    private static TileEntryTrapResolution.TrapLayer trap(
            String key,
            int layers,
            String sourceId,
            String sourceTeamId,
            String name
    ) {
        return new TileEntryTrapResolution.TrapLayer(
                key,
                layers,
                sourceId,
                sourceTeamId,
                Set.of("forest"),
                name
        );
    }
}
