package io.autoptu.core.rules;

import io.autoptu.core.random.PythonRandom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Python-parity primitive for Trainer Feature context gates.
 *
 * This deliberately excludes feature prerequisites, frequency/cooldowns, resource/AP
 * spending, usage mutation, target scopes and effect application. Callers must provide
 * observations derived from authoritative battle state; Minecraft/Cobblemon must not
 * decide these gates.
 */
public final class TrainerFeatureContextResolution {
    private TrainerFeatureContextResolution() {}

    public record Context(
            String trainerId,
            String actorId,
            String actorTrainerId,
            boolean actorIsPokemon,
            boolean actorActive,
            int currentRound,
            String battlePhase,
            Map<String, ?> payload,
            Map<String, Integer> featureUsage,
            PythonRandom rng
    ) {
        public Context {
            trainerId = trainerId == null ? "" : trainerId;
            actorId = actorId == null ? "" : actorId;
            actorTrainerId = actorTrainerId == null ? "" : actorTrainerId;
            battlePhase = battlePhase == null ? "" : battlePhase;
            payload = payload == null ? Map.of() : Map.copyOf(payload);
            featureUsage = featureUsage == null ? Map.of() : Map.copyOf(featureUsage);
        }
    }

    /** Mirrors TrainerFeatureDispatcher._feature_matches_context(). */
    public static boolean matches(Map<String, ?> feature, Context context) {
        Map<String, ?> safeFeature = feature == null ? Map.of() : feature;
        Context safeContext = context == null
                ? new Context("", "", "", false, false, 0, "", Map.of(), Map.of(), null)
                : context;

        Object rawConditions = safeFeature.get("conditions");
        if (rawConditions == null) rawConditions = safeFeature.get("condition");
        if (!(rawConditions instanceof Map<?, ?> conditions)) return true;

        if (boolLike(conditions.get("actor_required"), false) && safeContext.actorId().isBlank()) {
            return false;
        }

        String actorScope = normalizeToken(conditions.get("actor_scope"));
        if (List.of("self", "self_team", "ally", "allies", "own").contains(actorScope)
                && !safeContext.actorTrainerId().equals(safeContext.trainerId())) {
            return false;
        }
        if (List.of("enemy", "foe", "opponent").contains(actorScope)
                && (safeContext.actorTrainerId().isBlank()
                || safeContext.actorTrainerId().equals(safeContext.trainerId()))) {
            return false;
        }
        if (actorScope.equals("trainer") && !safeContext.actorId().equals(safeContext.trainerId())) {
            return false;
        }
        if (actorScope.equals("pokemon") && !safeContext.actorIsPokemon()) {
            return false;
        }

        List<String> phaseFilters = normalizeTokens(firstUntyped(conditions, "phase_in", "phase"));
        if (!phaseFilters.isEmpty()) {
            String phaseValue = normalizeToken(safeContext.payload().get("phase"));
            if (phaseValue.isBlank()) phaseValue = normalizeToken(safeContext.battlePhase());
            if (!phaseFilters.contains(phaseValue)) return false;
        }

        List<String> actionFilters = normalizeTokens(firstUntyped(conditions, "action_types", "action_type"));
        if (!actionFilters.isEmpty()
                && !actionFilters.contains(normalizeToken(safeContext.payload().get("action_type")))) {
            return false;
        }

        List<String> moveNameFilters = normalizeTokens(firstUntyped(conditions, "move_names", "move_name"));
        if (!moveNameFilters.isEmpty()
                && !moveNameFilters.contains(normalizeToken(safeContext.payload().get("move_name")))) {
            return false;
        }

        List<String> moveCategoryFilters = normalizeTokens(firstUntyped(conditions, "move_categories", "move_category"));
        if (!moveCategoryFilters.isEmpty()
                && !moveCategoryFilters.contains(normalizeToken(safeContext.payload().get("move_category")))) {
            return false;
        }

        if (conditions.containsKey("actor_active")) {
            boolean expectedActive = boolLike(conditions.get("actor_active"), false);
            if (!safeContext.actorIsPokemon() || safeContext.actorActive() != expectedActive) return false;
        }

        int minRound = intLike(conditions.get("min_round"), 0);
        int maxRound = intLike(conditions.get("max_round"), 0);
        if (minRound > 0 && safeContext.currentRound() < minRound) return false;
        if (maxRound > 0 && safeContext.currentRound() > maxRound) return false;

        int damage = intLike(
                safeContext.payload().get("damage"),
                intLike(
                        safeContext.payload().get("damage_dealt"),
                        intLike(safeContext.payload().get("total_damage"), 0)
                )
        );
        int minDamage = intLike(conditions.get("min_damage"), 0);
        int maxDamage = intLike(conditions.get("max_damage"), 0);
        if (minDamage > 0 && damage < minDamage) return false;
        if (maxDamage > 0 && damage > maxDamage) return false;

        if (boolLike(conditions.get("once_per_actor_per_round"), false)
                && !safeContext.actorId().isBlank()) {
            String key = "actor_round_" + safeContext.actorId() + "_" + safeContext.currentRound();
            if (safeContext.featureUsage().getOrDefault(key, 0) >= 1) return false;
        }

        Object rawChance = conditions.get("chance");
        if (rawChance != null && !String.valueOf(rawChance).isEmpty()) {
            double chance = floatLike(rawChance, 0.0);
            if (chance > 1.0) chance /= 100.0;
            chance = Math.max(0.0, Math.min(1.0, chance));
            if (chance <= 0.0) return false;
            double roll = safeContext.rng() == null ? 0.0 : safeContext.rng().random();
            if (roll >= chance) return false;
        }
        return true;
    }

    private static List<String> normalizeTokens(Object value) {
        if (value == null) return List.of();
        Collection<?> values = value instanceof Collection<?> collection
                ? collection
                : List.of(value);
        ArrayList<String> out = new ArrayList<>();
        for (Object entry : values) {
            String token = normalizeToken(entry);
            if (!token.isBlank()) out.add(token);
        }
        return List.copyOf(out);
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

    private static double floatLike(Object value, double fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value).strip());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean boolLike(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        String token = normalizeToken(value);
        if (List.of("1", "true", "yes", "y", "on").contains(token)) return true;
        if (List.of("0", "false", "no", "n", "off").contains(token)) return false;
        return fallback;
    }

    private static Object firstUntyped(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return value;
        }
        return null;
    }
}
