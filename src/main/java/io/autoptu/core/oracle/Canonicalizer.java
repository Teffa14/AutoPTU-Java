package io.autoptu.core.oracle;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java mirror of AutoPTU Career's _canonical_value helper.
 *
 * This class normalizes battle data before cross-language comparisons:
 * map keys become strings and are sorted, volatile fields are removed,
 * sequence order is preserved, and sets are sorted by their string form.
 */
public final class Canonicalizer {
    private static final Set<String> VOLATILE_KEYS = Set.of(
            "timestamp",
            "timestamp_utc",
            "time",
            "battle_log_path",
            "ai_diagnostics",
            "ai_learning",
            "ai_model"
    );

    private Canonicalizer() {
    }

    public static Object canonicalize(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }

        if (value instanceof Map<?, ?> map) {
            List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : entries) {
                String key = String.valueOf(entry.getKey());
                if (VOLATILE_KEYS.contains(key)) {
                    continue;
                }
                result.put(key, canonicalize(entry.getValue()));
            }
            return result;
        }

        if (value instanceof Set<?> set) {
            List<Object> entries = new ArrayList<>(set);
            entries.sort(Comparator.comparing(String::valueOf));
            List<Object> result = new ArrayList<>(entries.size());
            for (Object entry : entries) {
                result.add(canonicalize(entry));
            }
            return result;
        }

        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            for (Object entry : iterable) {
                result.add(canonicalize(entry));
            }
            return result;
        }

        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                result.add(canonicalize(Array.get(value, index)));
            }
            return result;
        }

        return String.valueOf(value);
    }

    public static Set<String> volatileKeys() {
        return VOLATILE_KEYS;
    }
}
