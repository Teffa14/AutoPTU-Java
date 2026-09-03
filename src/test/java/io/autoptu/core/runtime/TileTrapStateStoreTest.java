package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileTrapStateStoreTest {
    @Test
    void preservesPythonTileTrapAndSourceProvenanceUntilWholeKeyConsumption() {
        TileTrapStateStore store = new TileTrapStateStore();
        GridCoord coordinate = new GridCoord(4, 3);
        var abrasion = new TileEntryTrapResolution.TrapLayer(
                "abrasion_trap", 3, "source", "red", Set.of("mountain", "cave"), "Abrasion Trap");
        var second = new TileEntryTrapResolution.TrapLayer(
                "sticky_trap", 1, "other", "green", Set.of("forest"), "Sticky Trap");

        store.put(coordinate, abrasion);
        store.put(coordinate, second);

        assertEquals(List.of(abrasion, second), store.entries(coordinate));
        assertEquals(List.of(abrasion, second), store.snapshot().get(coordinate));

        assertTrue(store.consume(coordinate, " Abrasion_Trap "));
        assertEquals(List.of(second), store.entries(coordinate));
        assertFalse(store.consume(coordinate, "abrasion_trap"));
        assertEquals("other", store.entries(coordinate).getFirst().sourceId());
        assertEquals(Set.of("forest"), store.entries(coordinate).getFirst().terrains());
    }

    @Test
    void replaceRemovesEmptyTileAndKeepsNonPositiveLayersForResolverParity() {
        TileTrapStateStore store = new TileTrapStateStore();
        GridCoord coordinate = new GridCoord(1, 2);
        var negative = new TileEntryTrapResolution.TrapLayer(
                "negative", -2, "source", "red", Set.of(), "Negative");
        var zero = new TileEntryTrapResolution.TrapLayer(
                "zero", 0, "source", "red", Set.of(), "Zero");

        store.replace(coordinate, List.of(negative, zero));
        assertEquals(List.of(negative, zero), store.entries(coordinate));

        store.replace(coordinate, List.of());
        assertTrue(store.entries(coordinate).isEmpty());
        assertFalse(store.snapshot().containsKey(coordinate));
    }

    @Test
    void replacementUsesCanonicalTrapKeyLikePythonTrapMaps() {
        TileTrapStateStore store = new TileTrapStateStore();
        GridCoord coordinate = new GridCoord(0, 0);
        var first = new TileEntryTrapResolution.TrapLayer(
                "trap_key", 1, "source-a", "red", Set.of(), "Trap A");
        var replacement = new TileEntryTrapResolution.TrapLayer(
                " TRAP_KEY ", 2, "source-b", "blue", Set.of("wetlands"), "Trap B");

        store.put(coordinate, first);
        store.put(coordinate, replacement);

        assertEquals(List.of(replacement), store.entries(coordinate));
        assertEquals(2, store.entries(coordinate).getFirst().layers());
        assertEquals("source-b", store.entries(coordinate).getFirst().sourceId());
    }
}
