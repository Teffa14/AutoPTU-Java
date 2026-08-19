package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemporaryEffectPayloadOracleParityTest {
    @Test
    void javaStorePreservesRepresentativePythonTemporaryEffectPayloads() throws IOException {
        String fixturePath = System.getProperty("autoptu.temporary.effect.payload.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());

        TemporaryEffectStore store = new TemporaryEffectStore();
        for (String line : Files.readAllLines(Path.of(fixturePath))) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t");
            String scenario = parts[0];
            String name = parts[1];
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            for (int i = 2; i < parts.length; i++) {
                String[] field = parts[i].split("=", 2);
                String value = field[1];
                payload.put(field[0], value.matches("-?\\d+") ? Integer.parseInt(value) : value);
            }
            store.add(name, payload);

            TemporaryEffectEntry stored = store.entriesInInsertionOrder().get(store.entriesInInsertionOrder().size() - 1);
            assertEquals(name, stored.name(), scenario);
            assertEquals(payload, stored.payload(), scenario);
        }

        assertEquals(List.of("follow_me", "items_disabled", "ability_disabled"), store.namesInInsertionOrder());
    }

    @Test
    void payloadEntriesPreserveMultiplicityAndAreDefensivelyCopied() {
        TemporaryEffectStore store = new TemporaryEffectStore();
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("round", 3);

        store.add("Delayed", payload);
        store.add("delayed", Map.of("round", 4, "effect", "Future Sight"));
        payload.put("round", 99);

        assertEquals(2, store.count("delayed"));
        assertEquals(3, store.getAll("delayed").get(0).payload().get("round"));
        assertEquals(4, store.getAll("delayed").get(1).payload().get("round"));
        assertEquals(2, store.removeAll("DELAYED"));
        assertEquals(0, store.count("delayed"));
    }

    @Test
    void payloadRejectsNonScalarRuntimeObjects() {
        TemporaryEffectStore store = new TemporaryEffectStore();
        assertThrows(IllegalArgumentException.class, () -> store.add("bad", Map.of("object", new Object())));
    }
}
