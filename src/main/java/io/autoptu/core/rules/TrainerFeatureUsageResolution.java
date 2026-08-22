package io.autoptu.core.rules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Python-parity primitive for Trainer Feature usage/cooldown bookkeeping.
 *
 * Mirrors TrainerFeatureDispatcher._mark_feature_use(). This resolver runs only after a
 * Feature effect has actually applied. It deliberately excludes target/effect semantics,
 * resource consumption and AP so those concerns can remain separate parity contracts.
 */
public final class TrainerFeatureUsageResolution {
    private TrainerFeatureUsageResolution() {}

    public static Map<String, Map<String, Object>> markUse(
            Map<String, ?> feature,
            Map<String, ? extends Map<String, ?>> usage,
            int currentRound,
            String actorId
    ) {
        Map<String, Map<String, Object>> result = copyUsage(usage);
        String featureId = featureIdentifier(feature);
        Map<String, Object> info = new LinkedHashMap<>(result.getOrDefault(featureId, Map.of()));

        info.put("uses_total", directInt(info.get("uses_total")) + 1);
        info.put("last_round", currentRound);
        String roundKey = "uses_round_" + currentRound;
        info.put(roundKey, directInt(info.get(roundKey)) + 1);

        if (isPythonTruthyString(actorId)) {
            String actorRoundKey = "actor_round_" + actorId + "_" + currentRound;
            info.put(actorRoundKey, directInt(info.get(actorRoundKey)) + 1);
        }

        Object cooldownRaw;
        if (feature != null && feature.containsKey("cooldown_rounds")) {
            cooldownRaw = feature.get("cooldown_rounds");
        } else {
            cooldownRaw = feature == null ? 0 : feature.getOrDefault("cooldown", 0);
        }
        int cooldown = intLike(cooldownRaw, 0);
        if (cooldown > 0) {
            info.put("cooldown_until", currentRound + cooldown);
        }

        result.put(featureId, Collections.unmodifiableMap(info));
        return immutableUsage(result);
    }

    public static String featureIdentifier(Map<String, ?> feature) {
        Object raw = null;
        if (feature != null) {
            raw = firstPythonTruthy(feature.get("feature_id"), feature.get("id"), feature.get("name"));
        }
        String token = normalizeToken(raw).replace(" ", "-");
        return token.isEmpty() ? "feature" : token;
    }

    private static Map<String, Map<String, Object>> copyUsage(
            Map<String, ? extends Map<String, ?>> usage
    ) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (usage == null) return result;
        for (Map.Entry<String, ? extends Map<String, ?>> entry : usage.entrySet()) {
            Map<String, Object> info = new LinkedHashMap<>();
            if (entry.getValue() != null) info.putAll(entry.getValue());
            result.put(entry.getKey(), Collections.unmodifiableMap(info));
        }
        return result;
    }

    private static Map<String, Map<String, Object>> immutableUsage(
            Map<String, Map<String, Object>> usage
    ) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : usage.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(result);
    }

    /** Mirrors int(value or 0) used by Python usage counters. */
    private static int directInt(Object value) {
        if (isPythonFalsy(value)) return 0;
        if (value instanceof Boolean bool) return bool ? 1 : 0;
        if (value instanceof Number number) return number.intValue();
        String text = String.valueOf(value).strip();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Trainer Feature usage value is not Python-int-compatible: " + value, error);
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

    private static Object firstPythonTruthy(Object... values) {
        for (Object value : values) {
            if (!isPythonFalsy(value)) return value;
        }
        return null;
    }

    private static String normalizeToken(Object value) {
        return String.valueOf(value == null ? "" : value).strip().toLowerCase();
    }

    private static boolean isPythonTruthyString(String value) {
        return value != null && !value.isEmpty();
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
}