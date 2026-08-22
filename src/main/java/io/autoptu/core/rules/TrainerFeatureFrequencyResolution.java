package io.autoptu.core.rules;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Python-parity primitive for Trainer Feature frequency and cooldown eligibility.
 *
 * This contract deliberately excludes prerequisites, context conditions, resource/AP
 * spending, usage mutation, target scopes and effect application. Usage observations
 * must come from authoritative Trainer state; Minecraft/Cobblemon must not decide
 * availability or cooldowns.
 */
public final class TrainerFeatureFrequencyResolution {
    private static final Pattern FREQUENCY_LIMIT = Pattern.compile(
            "^\\s*(\\d+)\\s*/\\s*(round|turn|scene|encounter|daily)\\s*$"
    );

    private TrainerFeatureFrequencyResolution() {}

    public record Limits(int totalLimit, int roundLimit) {
        public Limits {
            totalLimit = Math.max(0, totalLimit);
            roundLimit = Math.max(0, roundLimit);
        }
    }

    /** Mirrors TrainerFeatureDispatcher._frequency_limits(). */
    public static Limits limits(Map<String, ?> feature) {
        Map<String, ?> safeFeature = feature == null ? Map.of() : feature;
        int totalLimit = intLike(safeFeature.get("max_uses"), 0);
        int roundLimit = intLike(safeFeature.get("uses_per_round"), 0);
        String token = frequencyToken(safeFeature);

        if (isOneOf(token, "daily", "scene", "encounter") && totalLimit <= 0) {
            totalLimit = 1;
        }
        if (isOneOf(token, "eot", "round", "turn", "x/round", "per-round", "per round")
                && roundLimit <= 0) {
            roundLimit = 1;
        }

        Matcher match = FREQUENCY_LIMIT.matcher(token);
        if (match.matches()) {
            int count = intLike(match.group(1), 0);
            String scope = normalizeToken(match.group(2));
            if (isOneOf(scope, "round", "turn") && roundLimit <= 0) {
                roundLimit = count;
            }
            if (isOneOf(scope, "daily", "scene", "encounter") && totalLimit <= 0) {
                totalLimit = count;
            }
        }
        return new Limits(totalLimit, roundLimit);
    }

    /** Mirrors TrainerFeatureDispatcher._feature_is_available(). */
    public static boolean isAvailable(
            Map<String, ?> feature,
            Map<String, ?> usageInfo,
            int currentRound
    ) {
        Map<String, ?> safeUsage = usageInfo == null ? Map.of() : usageInfo;
        int cooldownUntil = intLike(safeUsage.get("cooldown_until"), 0);
        if (cooldownUntil != 0 && currentRound <= cooldownUntil) {
            return false;
        }

        Limits limits = limits(feature);
        if (limits.totalLimit() > 0
                && intLike(safeUsage.get("uses_total"), 0) >= limits.totalLimit()) {
            return false;
        }

        String roundKey = "uses_round_" + currentRound;
        if (limits.roundLimit() > 0
                && intLike(safeUsage.get(roundKey), 0) >= limits.roundLimit()) {
            return false;
        }
        return true;
    }

    private static String frequencyToken(Map<String, ?> feature) {
        Object raw = feature.get("frequency");
        if (raw == null || String.valueOf(raw).isBlank()) return "at-will";
        return normalizeToken(raw);
    }

    private static boolean isOneOf(String value, String... options) {
        for (String option : options) {
            if (option.equals(value)) return true;
        }
        return false;
    }

    private static String normalizeToken(Object value) {
        return value == null ? "" : String.valueOf(value).strip().toLowerCase(Locale.ROOT);
    }

    private static int intLike(Object value, int fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        if (value instanceof Number number) return number.intValue();
        String text = String.valueOf(value).strip();
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
}
