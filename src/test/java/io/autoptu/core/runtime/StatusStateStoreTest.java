package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusStateStoreTest {
    @Test
    void preservesCanonicalMetadataAndLegacyNameView() {
        LinkedHashMap<String, Object> sourcePayload = new LinkedHashMap<>();
        sourcePayload.put("applied_round", 4);
        sourcePayload.put("source", "move:fake-out");
        StatusEntry flinch = new StatusEntry(" Flinched ", sourcePayload);
        sourcePayload.put("applied_round", 99);

        StatusStateStore store = new StatusStateStore();
        store.replace("actor", List.of(flinch, new StatusEntry("Burned")));

        assertEquals(List.of("flinched", "burned"), store.entries("actor").stream().map(StatusEntry::name).toList());
        assertTrue(store.has("actor", "FLINCHED"));
        assertTrue(store.has("actor", "burned"));
        assertEquals(4, store.find("actor", "flinchED").orElseThrow().intPayload("applied_round").orElseThrow());
        assertEquals("move:fake-out", store.find("actor", "flinched").orElseThrow().stringPayload("source").orElseThrow());
    }

    @Test
    void legacyNamesRemainSetLikeWhileStructuredEntriesCanStack() {
        StatusStateStore store = new StatusStateStore();
        store.replaceNames("actor", List.of(" Sleep ", "sleep", "Poisoned"));

        assertEquals(2, store.entries("actor").size());
        assertTrue(store.find("actor", "sleep").orElseThrow().payload().isEmpty());
        assertTrue(store.find("actor", "poisoned").orElseThrow().payload().isEmpty());

        store.replace("actor", List.of(
                new StatusEntry("Poisoned", Map.of("source", "move:a")),
                new StatusEntry("POISONED", Map.of("source", "move:b")),
                new StatusEntry("Burned")
        ));

        assertEquals(List.of("poisoned", "poisoned", "burned"), store.entries("actor").stream().map(StatusEntry::name).toList());
        assertEquals(2, store.findAll("actor", "poisoned").size());
        assertEquals("move:a", store.find("actor", "poisoned").orElseThrow().stringPayload("source").orElseThrow());
    }

    @Test
    void putReplacesFirstNormalizedStatusWithoutCollapsingLaterStacks() {
        StatusStateStore store = new StatusStateStore();
        store.replace("actor", List.of(
                new StatusEntry("flinch", Map.of("applied_round", 1)),
                new StatusEntry("burned"),
                new StatusEntry("flinch", Map.of("applied_round", 7))
        ));
        store.put("actor", new StatusEntry("FLINCH", Map.of("applied_round", 2)));

        assertEquals(List.of("flinch", "burned", "flinch"), store.entries("actor").stream().map(StatusEntry::name).toList());
        assertEquals(2, store.find("actor", "flinch").orElseThrow().intPayload("applied_round").orElseThrow());
        assertEquals(7, store.findAll("actor", "flinch").get(1).intPayload("applied_round").orElseThrow());
    }

    @Test
    void appendAndRemovePreservePythonStyleStackOrder() {
        StatusStateStore store = new StatusStateStore();
        store.append("actor", new StatusEntry("poisoned", Map.of("source", "one")));
        store.append("actor", new StatusEntry("burned"));
        store.append("actor", new StatusEntry("POISONED", Map.of("source", "two")));

        assertTrue(store.remove("actor", "poisoned"));
        assertEquals(List.of("burned", "poisoned"), store.entries("actor").stream().map(StatusEntry::name).toList());
        assertEquals("two", store.find("actor", "poisoned").orElseThrow().stringPayload("source").orElseThrow());
        assertEquals(1, store.removeAll("actor", "poisoned"));
        assertFalse(store.has("actor", "poisoned"));
        assertTrue(store.has("actor", "burned"));
        assertEquals(1, store.clear("actor"));
        assertTrue(store.entries("actor").isEmpty());
    }

    @Test
    void rejectsNonScalarPayloads() {
        assertThrows(IllegalArgumentException.class, () -> new StatusEntry("flinch", Map.of("nested", List.of(1))));
    }
}
