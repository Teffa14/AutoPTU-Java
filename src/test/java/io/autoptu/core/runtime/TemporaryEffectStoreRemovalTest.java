package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TemporaryEffectStoreRemovalTest {
    @Test
    void removeAllEntriesReturnsSnapshotsInInsertionOrderAndPreservesOtherFamilies() {
        TemporaryEffectStore store = new TemporaryEffectStore();
        store.add("psionic_sponge_move", Map.of("move", "Confusion", "source", "A"));
        store.add("terrain_alias", Map.of("feature", "Other Feature"));
        store.add("PSIONIC_SPONGE_MOVE", Map.of("move", "Psybeam", "source", "B"));

        List<TemporaryEffectEntry> removed = store.removeAllEntries(" psionic_sponge_move ");

        assertEquals(2, removed.size());
        assertEquals("Confusion", removed.get(0).payload().get("move"));
        assertEquals("Psybeam", removed.get(1).payload().get("move"));
        assertEquals(0, store.count("psionic_sponge_move"));
        assertEquals(1, store.count("terrain_alias"));
    }

    @Test
    void removeAllEntriesPreservesMultiplicityAndReturnsImmutableSnapshot() {
        TemporaryEffectStore store = new TemporaryEffectStore();
        store.add("psionic_sponge_move", Map.of("move", "Confusion"));
        store.add("psionic_sponge_move", Map.of("move", "Confusion"));
        store.add("psionic_sponge_move", Map.of("move", ""));

        List<TemporaryEffectEntry> removed = store.removeAllEntries("psionic_sponge_move");

        assertEquals(3, removed.size());
        assertEquals(List.of("Confusion", "Confusion", ""), removed.stream()
                .map(entry -> String.valueOf(entry.payload().get("move")))
                .toList());
        assertTrue(store.entriesInInsertionOrder().isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> removed.add(new TemporaryEffectEntry("other"))
        );
    }

    @Test
    void removeAllEntriesReturnsEmptySnapshotWhenFamilyIsAbsent() {
        TemporaryEffectStore store = new TemporaryEffectStore();
        store.add("terrain_alias", Map.of("feature", "Adaptive Geography"));

        List<TemporaryEffectEntry> removed = store.removeAllEntries("psionic_sponge_move");

        assertTrue(removed.isEmpty());
        assertEquals(1, store.count("terrain_alias"));
    }
}
