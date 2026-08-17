package io.autoptu.core.oracle;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CanonicalizerTest {
    @Test
    void sortsMapKeysAndDropsVolatileFields() {
        Map<Object, Object> input = new LinkedHashMap<>();
        input.put("z", 3);
        input.put("timestamp", "discard-me");
        input.put(2, "numeric-key");
        input.put("a", Map.of("time", 99, "kept", true));

        Object normalized = Canonicalizer.canonicalize(input);
        Map<?, ?> result = assertInstanceOf(Map.class, normalized);

        assertEquals(List.of("2", "a", "z"), List.copyOf(result.keySet()));
        assertFalse(result.containsKey("timestamp"));
        assertEquals("numeric-key", result.get("2"));
        assertEquals(Map.of("kept", true), result.get("a"));
    }

    @Test
    void preservesSequenceOrderButSortsSetsByStringForm() {
        assertEquals(
                List.of("b", "a", 2),
                Canonicalizer.canonicalize(List.of("b", "a", 2))
        );
        assertEquals(
                List.of(10, 2, "3"),
                Canonicalizer.canonicalize(Set.of(2, 10, "3"))
        );
    }

    @Test
    void canonicalizesArraysAndNestedValues() {
        Object[] input = {
                Map.of("ai_model", "discard", "value", 4),
                new int[]{3, 1, 2}
        };

        assertEquals(
                List.of(Map.of("value", 4), List.of(3, 1, 2)),
                Canonicalizer.canonicalize(input)
        );
    }
}
