package io.autoptu.core.rules;

import java.util.Locale;
import java.util.Map;

/**
 * First deterministic calculation primitives ported from
 * auto_ptu/rules/calculations.py.
 *
 * Keep formulas behaviorally identical to the Python oracle. More combat
 * calculations should be added here only with parity tests.
 */
public final class Calculations {
    public static final int STAGE_MIN = -6;
    public static final int STAGE_MAX = 6;

    private static final Map<String, Map<String, Integer>> WEATHER_DB_MODIFIERS = Map.of(
            "rain", Map.of("electric", 1, "water", 1, "fire", -1),
            "storm", Map.of("electric", 1, "water", 1, "fire", -1),
            "downpour", Map.of("electric", 1, "water", 1, "fire", -1),
            "sun", Map.of("fire", 1, "water", -1),
            "sunny", Map.of("fire", 1, "water", -1),
            "harsh sunlight", Map.of("fire", 1, "water", -1),
            "hail", Map.of("ice", 1),
            "sandstorm", Map.of("rock", 1)
    );

    private Calculations() {
    }

    public static int clampStage(int value) {
        return Math.max(STAGE_MIN, Math.min(STAGE_MAX, value));
    }

    public static double stageMultiplier(int stage) {
        int clamped = clampStage(stage);
        if (clamped >= 0) {
            return (2.0 + clamped) / 2.0;
        }
        return 2.0 / (2.0 - clamped);
    }

    /** Accuracy stages are a flat modifier in the Python engine. */
    public static int accuracyStageValue(int stage) {
        return clampStage(stage);
    }

    public static int weatherDbModifier(String weather, String moveType) {
        String normalizedWeather = normalize(weather);
        String normalizedType = normalize(moveType);
        return WEATHER_DB_MODIFIERS
                .getOrDefault(normalizedWeather, Map.of())
                .getOrDefault(normalizedType, 0);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
