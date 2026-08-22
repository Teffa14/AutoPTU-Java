package io.autoptu.core.rules;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Transactional composition of the generic Trainer Feature dispatcher gates.
 *
 * Mirrors the ordering of Python TrainerFeatureDispatcher.trigger(): enabled/trigger,
 * prerequisites, context, frequency, resources, effect application, then resource
 * consumption and usage/cooldown mutation. Target resolution and concrete effect
 * semantics stay behind {@link FeatureEffect} so those families can be ported separately.
 * Minecraft/Cobblemon must not decide eligibility or commit bookkeeping.
 */
public final class TrainerFeatureExecutionService {
    private TrainerFeatureExecutionService() {}

    public enum Outcome {
        DISABLED,
        TRIGGER_MISMATCH,
        PREREQUISITES_FAILED,
        CONTEXT_FAILED,
        FREQUENCY_BLOCKED,
        RESOURCES_BLOCKED,
        EFFECT_NOT_APPLIED,
        APPLIED
    }

    @FunctionalInterface
    public interface FeatureEffect {
        boolean apply();
    }

    public record Result(
            Outcome outcome,
            Map<String, Object> resources,
            Map<String, Map<String, Object>> usage
    ) {
        public Result {
            resources = immutableResources(resources);
            usage = immutableUsage(usage);
        }

        public boolean applied() {
            return outcome == Outcome.APPLIED;
        }
    }

    public static Result execute(
            String trigger,
            Map<String, ?> trainerClass,
            Map<String, ?> feature,
            Collection<String> knownFeatureIds,
            TrainerFeatureContextResolution.Context context,
            Map<String, ?> resources,
            Map<String, ? extends Map<String, ?>> usage,
            FeatureEffect effect
    ) {
        Map<String, ?> safeFeature = feature == null ? Map.of() : feature;
        Map<String, Object> initialResources = mutableResources(resources);
        Map<String, Map<String, Object>> initialUsage = mutableUsage(usage);

        if (!isEnabled(safeFeature)) {
            return result(Outcome.DISABLED, initialResources, initialUsage);
        }
        if (!normalize(safeFeature.get("trigger")).equals(normalize(trigger))) {
            return result(Outcome.TRIGGER_MISMATCH, initialResources, initialUsage);
        }
        if (!TrainerFeaturePrerequisiteResolution.prerequisitesMet(
                trainerClass,
                safeFeature,
                knownFeatureIds
        )) {
            return result(Outcome.PREREQUISITES_FAILED, initialResources, initialUsage);
        }

        TrainerFeatureContextResolution.Context effectiveContext = withFeatureUsage(
                context,
                usageInfo(safeFeature, initialUsage)
        );
        if (!TrainerFeatureContextResolution.matches(safeFeature, effectiveContext)) {
            return result(Outcome.CONTEXT_FAILED, initialResources, initialUsage);
        }

        int currentRound = effectiveContext.currentRound();
        Map<String, Object> currentUsage = usageInfo(safeFeature, initialUsage);
        if (!TrainerFeatureFrequencyResolution.isAvailable(safeFeature, currentUsage, currentRound)) {
            return result(Outcome.FREQUENCY_BLOCKED, initialResources, initialUsage);
        }
        if (!TrainerFeatureResourceResolution.hasResources(safeFeature, initialResources)) {
            return result(Outcome.RESOURCES_BLOCKED, initialResources, initialUsage);
        }

        if (effect == null || !effect.apply()) {
            return result(Outcome.EFFECT_NOT_APPLIED, initialResources, initialUsage);
        }

        Map<String, Object> committedResources = TrainerFeatureResourceResolution.consume(
                safeFeature,
                initialResources
        );
        Map<String, Map<String, Object>> committedUsage = TrainerFeatureUsageResolution.markUse(
                safeFeature,
                initialUsage,
                currentRound,
                effectiveContext.actorId().isBlank() ? null : effectiveContext.actorId()
        );
        return result(Outcome.APPLIED, committedResources, committedUsage);
    }

    private static TrainerFeatureContextResolution.Context withFeatureUsage(
            TrainerFeatureContextResolution.Context context,
            Map<String, Integer> featureUsage
    ) {
        TrainerFeatureContextResolution.Context source = context == null
                ? new TrainerFeatureContextResolution.Context(
                        "", "", "", false, false, 0, "", Map.of(), Map.of(), null
                )
                : context;
        return new TrainerFeatureContextResolution.Context(
                source.trainerId(),
                source.actorId(),
                source.actorTrainerId(),
                source.actorIsPokemon(),
                source.actorActive(),
                source.currentRound(),
                source.battlePhase(),
                source.payload(),
                featureUsage,
                source.rng()
        );
    }

    private static Map<String, Integer> usageInfo(
            Map<String, ?> feature,
            Map<String, ? extends Map<String, ?>> usage
    ) {
        String featureId = TrainerFeatureUsageResolution.featureIdentifier(feature);
        Map<String, ?> raw = usage.get(featureId);
        if (raw == null) return Map.of();
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : raw.entrySet()) {
            Object value = entry.getValue();
            if (value == null || "".equals(value)) {
                result.put(entry.getKey(), 0);
            } else if (value instanceof Number number) {
                result.put(entry.getKey(), number.intValue());
            } else if (value instanceof Boolean bool) {
                result.put(entry.getKey(), bool ? 1 : 0);
            } else {
                try {
                    result.put(entry.getKey(), Integer.parseInt(String.valueOf(value).strip()));
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException(
                            "Trainer Feature usage value is not Python-int-compatible: " + value,
                            error
                    );
                }
            }
        }
        return Map.copyOf(result);
    }

    private static boolean isEnabled(Map<String, ?> feature) {
        if (!feature.containsKey("enabled")) return true;
        return pythonTruthy(feature.get("enabled"));
    }

    private static boolean pythonTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.doubleValue() != 0.0;
        if (value instanceof CharSequence sequence) return !sequence.isEmpty();
        if (value instanceof Collection<?> collection) return !collection.isEmpty();
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        return true;
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).strip().toLowerCase(Locale.ROOT);
    }

    private static Result result(
            Outcome outcome,
            Map<String, ?> resources,
            Map<String, ? extends Map<String, ?>> usage
    ) {
        return new Result(outcome, mutableResources(resources), mutableUsage(usage));
    }

    private static Map<String, Object> mutableResources(Map<String, ?> resources) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (resources != null) result.putAll(resources);
        return result;
    }

    private static Map<String, Map<String, Object>> mutableUsage(
            Map<String, ? extends Map<String, ?>> usage
    ) {
        LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (usage == null) return result;
        for (Map.Entry<String, ? extends Map<String, ?>> entry : usage.entrySet()) {
            LinkedHashMap<String, Object> info = new LinkedHashMap<>();
            if (entry.getValue() != null) info.putAll(entry.getValue());
            result.put(entry.getKey(), info);
        }
        return result;
    }

    private static Map<String, Object> immutableResources(Map<String, ?> resources) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(mutableResources(resources)));
    }

    private static Map<String, Map<String, Object>> immutableUsage(
            Map<String, ? extends Map<String, ?>> usage
    ) {
        LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : mutableUsage(usage).entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(result);
    }
}
