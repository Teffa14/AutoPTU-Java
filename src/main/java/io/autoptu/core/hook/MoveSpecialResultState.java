package io.autoptu.core.hook;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable result mapping shared by move-special handlers during one dispatch.
 *
 * <p>Python move specials receive the same result dictionary and may mutate it in-place. This
 * state object preserves that contract while keeping the mapping owned by the Java battle core.</p>
 */
public final class MoveSpecialResultState {
    private final LinkedHashMap<String, Object> values;

    public MoveSpecialResultState(Map<String, ?> initialValues) {
        values = new LinkedHashMap<>();
        if (initialValues != null) {
            initialValues.forEach((key, value) -> {
                if (key == null || key.isBlank()) {
                    throw new IllegalArgumentException("result key is required");
                }
                values.put(key, value);
            });
        }
    }

    public static MoveSpecialResultState withHit(boolean hit) {
        return new MoveSpecialResultState(Map.of("hit", hit));
    }

    public Object get(String key) {
        return values.get(key);
    }

    public Object getOrDefault(String key, Object defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public void put(String key, Object value) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("result key is required");
        values.put(key, value);
    }

    public boolean hit() {
        return pythonTruthy(values.get("hit"));
    }

    public Map<String, Object> snapshot() {
        return Map.copyOf(values);
    }

    private static boolean pythonTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.doubleValue() != 0.0d;
        if (value instanceof CharSequence sequence) return !sequence.isEmpty();
        return true;
    }
}
