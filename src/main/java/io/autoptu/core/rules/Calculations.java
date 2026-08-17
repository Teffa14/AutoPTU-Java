package io.autoptu.core.rules;

import io.autoptu.core.model.AttackModifier;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic calculation primitives ported from auto_ptu/rules/calculations.py.
 *
 * Keep formulas behaviorally identical to the Python oracle. Stateful ability,
 * item, and status resolution belongs in higher-level adapters until its own
 * parity slice is ready.
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

    /** Mirror Python crit_probability(move, hit_chance). */
    public static double critProbability(int critRange, double hitChance) {
        int threshold = critRange == 0 ? 20 : critRange;
        int successFaces = Math.max(0, 21 - threshold);
        double probability = successFaces / 20.0;
        return Math.min(probability, hitChance);
    }

    /** Mirror the legacy physical Burn damage modifier. */
    public static int applyStatusModifiers(int baseDamage, String moveCategory, boolean burned) {
        if ("physical".equals(normalize(moveCategory)) && burned) {
            return (int) Math.floor(baseDamage * 0.5);
        }
        return baseDamage;
    }

    /**
     * Mirror apply_context_damage_modifiers: all flat modifiers are applied first,
     * then scalar modifiers are applied in declaration order with floor after each.
     */
    public static int applyContextDamageModifiers(int baseDamage, List<AttackModifier> modifiers) {
        int damage = baseDamage;
        List<AttackModifier> safe = modifiers == null ? List.of() : modifiers;
        for (AttackModifier modifier : safe) {
            if ("damage_flat".equals(modifier.kind())) {
                damage += (int) modifier.value();
            }
        }
        for (AttackModifier modifier : safe) {
            if ("damage_scalar".equals(modifier.kind())) {
                damage = (int) Math.floor(damage * modifier.value());
            }
        }
        return damage;
    }

    /** Mirror calculations._normalized_range_kind. */
    public static String normalizedRangeKind(String rangeKind, String targetKind) {
        String kind = firstNonBlank(rangeKind, targetKind, "ranged").toLowerCase(Locale.ROOT);
        return kind.contains("melee") ? "melee" : "ranged";
    }

    /** Common final step in Python resolve_move_action. */
    public static int applyTypeMultiplierFloor(int baseDamage, double typeMultiplier) {
        return (int) Math.floor(baseDamage * typeMultiplier);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
