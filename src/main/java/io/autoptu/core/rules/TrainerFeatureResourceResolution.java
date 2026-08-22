package io.autoptu.core.rules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Python-parity primitive for Trainer Feature resource availability and consumption.
 *
 * Mirrors TrainerFeatureDispatcher._feature_has_resources() and _consume_resources().
 * This layer deliberately excludes AP, frequency/usage bookkeeping, target scopes and
 * effect application. Resource state is authoritative server state; Minecraft/Cobblemon
 * must not decide whether a Feature is affordable or mutate the resulting balances.
 */
public final class TrainerFeatureResourceResolution {
    private TrainerFeatureResourceResolution() {}

    /** Mirrors TrainerFeatureDispatcher._feature_has_resources(). */
    public static boolean hasResources(Map<String, ?> feature, Map<String, ?> resources) {
        Map<?, ?> costs = resourceCosts(feature);
        if (costs == null) return true;
        Map<String, ?> safeResources = resources == null ? Map.of() : resources;

        for (Map.Entry<?, ?> entry : costs.entrySet()) {
            String token = pythonString(entry.getKey());
            int need = intLike(entry.getValue(), 0);
            if (need <= 0) continue;
            if (resourceInt(safeResources.get(token)) < need) return false;
        }
        return true;
    }

    /**
     * Mirrors TrainerFeatureDispatcher._consume_resources().
     *
     * The returned snapshot is immutable. Consumption itself clamps each charged resource
     * at zero exactly like Python; callers are expected to invoke this only after the
     * Feature effect applied, matching TrainerFeatureDispatcher.trigger().
     */
    public static Map<String, Object> consume(Map<String, ?> feature, Map<String, ?> resources) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (resources != null) result.putAll(resources);

        Map<?, ?> costs = resourceCosts(feature);
        if (costs == null) return Collections.unmodifiableMap(result);

        for (Map.Entry<?, ?> entry : costs.entrySet()) {
            String token = pythonString(entry.getKey());
            int need = intLike(entry.getValue(), 0);
            if (need <= 0) continue;
            int current = resourceInt(result.get(token));
            result.put(token, Math.max(0, current - need));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<?, ?> resourceCosts(Map<String, ?> feature) {
        if (feature == null) return Map.of();
        Object raw = feature.get("resource_cost");
        if (isPythonFalsy(raw)) return Map.of();
        return raw instanceof Map<?, ?> map ? map : null;
    }

    /** Mirrors trainer resource lookup's direct int(value or 0), not _int_like(). */
    private static int resourceInt(Object value) {
        if (isPythonFalsy(value)) return 0;
        if (value instanceof Boolean bool) return bool ? 1 : 0;
        if (value instanceof Number number) return number.intValue();
        String text = String.valueOf(value).strip();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Trainer Feature resource balance is not Python-int-compatible: " + value, error);
        }
    }

    /** Mirrors trainer_features._int_like(). */
    private static int intLike(Object value, int fallback) {
        if (value == null || "".equals(value)) return fallback;
        if (value instanceof Boolean bool) return bool ? 1 : 0;
        if (value instanceof Number number) return number.intValue();
        String text = String.valueOf(value).strip();
        if (text.isEmpty()) return fallback;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            try {
                return (int) Double.parseDouble(text);
            } catch (NumberFormatException ignoredAgain) {
                return fallback;
            }
        }
    }

    private static boolean isPythonFalsy(Object value) {
        if (value == null) return true;
        if (value instanceof Boolean bool) return !bool;
        if (value instanceof Number number) return number.doubleValue() == 0.0;
        if (value instanceof CharSequence sequence) return sequence.isEmpty();
        if (value instanceof Map<?, ?> map) return map.isEmpty();
        if (value instanceof java.util.Collection<?> collection) return collection.isEmpty();
        return false;
    }

    private static String pythonString(Object value) {
        if (value == null) return "None";
        if (value instanceof Boolean bool) return bool ? "True" : "False";
        return String.valueOf(value);
    }
}
