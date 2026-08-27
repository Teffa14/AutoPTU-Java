package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeldItemStartTemporaryEffectResolutionTest {
    @Test
    void materializesGenericFamiliesInPythonOrder() {
        TemporaryEffectStore store = new TemporaryEffectStore();
        HeldItemStartTemporaryEffectResolution.apply(store, new HeldItemStartTemporaryEffectResolution.Input(
                "Test Charm",
                List.of(
                        new HeldItemStartTemporaryEffectResolution.StatAmount("atk", 5),
                        new HeldItemStartTemporaryEffectResolution.StatAmount("spdef", 3)
                ),
                List.of(new HeldItemStartTemporaryEffectResolution.StatScalar("spd", 1.5)),
                2,
                1,
                2
        ));

        assertEquals(List.of(
                "stat_modifier", "stat_modifier", "stat_scalar", "accuracy_bonus", "evasion_bonus", "evasion_bonus"
        ), store.entriesInInsertionOrder().stream().map(TemporaryEffectEntry::name).toList());
        assertEquals(Map.of("stat", "atk", "amount", 5, "source", "Test Charm"), store.entriesInInsertionOrder().get(0).payload());
        assertEquals(Map.of("stat", "spd", "multiplier", 1.5, "source", "Test Charm"), store.entriesInInsertionOrder().get(2).payload());
        assertEquals(2, store.entriesInInsertionOrder().get(3).payload().get("amount"));
        assertEquals(null, store.entriesInInsertionOrder().get(3).payload().get("type"));
        assertEquals("status", store.entriesInInsertionOrder().get(4).payload().get("scope"));
        assertEquals("all", store.entriesInInsertionOrder().get(5).payload().get("scope"));
    }

    @Test
    void preservesPythonDuplicateGuardsPerFamilyAndSource() {
        TemporaryEffectStore store = new TemporaryEffectStore();
        store.add("stat_modifier", Map.of("stat", "atk", "amount", 99, "source", "Test Charm"));
        store.add("stat_scalar", Map.of("stat", "spd", "multiplier", 9.0, "source", "Test Charm"));

        HeldItemStartTemporaryEffectResolution.Input input = new HeldItemStartTemporaryEffectResolution.Input(
                "Test Charm",
                List.of(new HeldItemStartTemporaryEffectResolution.StatAmount("atk", 5)),
                List.of(new HeldItemStartTemporaryEffectResolution.StatScalar("spd", 1.5)),
                2,
                1,
                2
        );
        HeldItemStartTemporaryEffectResolution.apply(store, input);
        HeldItemStartTemporaryEffectResolution.apply(store, input);

        assertEquals(1, store.count("stat_modifier"));
        assertEquals(1, store.count("stat_scalar"));
        assertEquals(1, store.count("accuracy_bonus"));
        assertEquals(2, store.count("evasion_bonus"));
        assertEquals(99, store.getAll("stat_modifier").get(0).payload().get("amount"));
        assertEquals(9.0, store.getAll("stat_scalar").get(0).payload().get("multiplier"));
    }
}
