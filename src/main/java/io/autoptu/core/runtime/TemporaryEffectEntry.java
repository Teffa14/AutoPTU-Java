package io.autoptu.core.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One immutable server-owned temporary effect entry.
 *
 * Python temporary effects are dictionaries that carry a normalized effect name plus
 * arbitrary metadata such as round numbers, move identities, targets, or expiry data.
 * This record keeps the Java boundary language-neutral by storing only scalar payload
 * values that serialize cleanly across tests/adapters.
 */
public record TemporaryEffectEntry(String name, Map<String, Object> payload) {
    public TemporaryEffectEntry {
        name = normalizeName(name);
        payload = immutableScalarPayload(payload);
    }

    public TemporaryEffectEntry(String name) {
        this(name, Map.of());
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("effect name is required");
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
                throw new IllegalArgumentException("temporary-effect payload keys must be non-blank");
            }
            Object value = entry.getValue();
            if (value != null
                    && !(value instanceof String)
                    && !(value instanceof Integer)
                    && !(value instanceof Long)
                    && !(value instanceof Double)
                    && !(value instanceof Boolean)) {
                throw new IllegalArgumentException(
                        "temporary-effect payload values must be null, String, Integer, Long, Double, or Boolean: "
                                + key
                );
            }
            copied.put(key, value);
        }
        return Collections.unmodifiableMap(copied);
    }
}
