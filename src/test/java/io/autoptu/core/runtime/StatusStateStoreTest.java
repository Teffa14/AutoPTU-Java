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
    void legacyNamesBecomeMetadataFreeEntries() {
        StatusStateStore store = new StatusStateStore();
        store.replaceNames("actor", List.of(" Sleep ", "sleep", "Poisoned"));

        assertEquals(2, store.entries("actor").size());
        assertTrue(store.find("actor", "sleep").orElseThrow().payload().isEmpty());
        assertTrue(store.find("actor", "poisoned").orElseThrow().payload().isEmpty());
    }

    @Test
    void putReplacesOneNormalizedStatusWithoutChangingOrder() {
        StatusStateStore store = new StatusStateStore();
        store.replace("actor", List.of(
                new StatusEntry("flinch", Map.of("applied_round", 1)),
                new StatusEntry("burned")
        ));
        store.put("actor", new StatusEntry("FLINCH", Map.of("applied_round", 2)));

        assertEquals(List.of("flinch", "burned"), store.entries("actor").stream().map(StatusEntry::name).toList());
        assertEquals(2, store.find("actor", "flinch").orElseThrow().intPayload("applied_round").orElseThrow());
    }

    @Test
    void removeCleansOnlyRequestedStatus() {
        StatusStateStore store = new StatusStateStore();
        store.replaceNames("actor", List.of("flinch", "burned"));

        assertTrue(store.remove("actor", "FLINCH"));
        assertFalse(store.has("actor", "flinch"));
        assertTrue(store.has("actor", "burned"));
        assertFalse(store.remove("actor", "flinch"));
    }

    @Test
    void rejectsDuplicateStructuredEntriesAndNonScalarPayloads() {
        StatusStateStore store = new StatusStateStore();
        assertThrows(IllegalArgumentException.class, () -> store.replace("actor", List.of(
                new StatusEntry("flinch"),
                new StatusEntry("FLINCH")
        )));
        assertThrows(IllegalArgumentException.class, () -> new StatusEntry("flinch", Map.of("nested", List.of(1))));
    }
}
