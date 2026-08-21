package io.autoptu.core.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable canonical terrain/zone/room entry with optional round duration metadata. */
public record FieldEffectEntry(
        FieldEffectKind kind,
        String name,
        Integer remaining,
        Map<String, Object> payload
) {
    public FieldEffectEntry {
        if (kind == null) throw new IllegalArgumentException("field effect kind is required");
        name = name == null ? "" : name.strip();
        if (name.isBlank()) throw new IllegalArgumentException("field effect name is required");
        payload = immutableScalarPayload(payload);
    }

    public FieldEffectEntry(FieldEffectKind kind, String name, Integer remaining) {
        this(kind, name, remaining, Map.of());
    }

    public FieldEffectEntry withRemaining(Integer nextRemaining) {
        return new FieldEffectEntry(kind, name, nextRemaining, payload);
    }

    private static Map<String, Object> immutableScalarPayload(Map<String, ?> source) {
        if (source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<String, Object> copied = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("field-effect payload keys must be non-blank");
            }
            Object value = entry.getValue();
            if (value != null
                    && !(value instanceof String)
                    && !(value instanceof Integer)
                    && !(value instanceof Long)
                    && !(value instanceof Double)
                    && !(value instanceof Boolean)) {
                throw new IllegalArgumentException("field-effect payload values must be scalar: " + key);
            }
            copied.put(key, value);
        }
        return Collections.unmodifiableMap(copied);
    }
}
