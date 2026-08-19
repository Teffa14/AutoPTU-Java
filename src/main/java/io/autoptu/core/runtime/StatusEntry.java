package io.autoptu.core.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable server-owned status entry with language-neutral metadata.
 *
 * Python status behavior sometimes depends on per-status payload such as the round
 * applied, remaining duration, or source identity. Minecraft/Cobblemon may render
 * this state but cannot author rule metadata supplied to the battle core.
 */
public record StatusEntry(String name, Map<String, Object> payload) {
    public StatusEntry {
        name = normalizeName(name);
        payload = immutableScalarPayload(payload);
    }

    public StatusEntry(String name) {
        this(name, Map.of());
    }

    public Optional<Integer> intPayload(String key) {
        Object value = payload.get(key);
        if (value instanceof Integer integer) {
            return Optional.of(integer);
        }
        if (value instanceof Long longValue
                && longValue >= Integer.MIN_VALUE
                && longValue <= Integer.MAX_VALUE) {
            return Optional.of(longValue.intValue());
        }
        return Optional.empty();
    }

    public Optional<String> stringPayload(String key) {
        Object value = payload.get(key);
        return value instanceof String string ? Optional.of(string) : Optional.empty();
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("status name is required");
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Object> immutableScalarPayload(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> copied = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("status payload keys must be non-blank");
            }
            Object value = entry.getValue();
            if (value != null
                    && !(value instanceof String)
                    && !(value instanceof Integer)
                    && !(value instanceof Long)
                    && !(value instanceof Double)
                    && !(value instanceof Boolean)) {
                throw new IllegalArgumentException(
                        "status payload values must be null, String, Integer, Long, Double, or Boolean: " + key
                );
            }
            copied.put(key, value);
        }
        return Collections.unmodifiableMap(copied);
    }
}
