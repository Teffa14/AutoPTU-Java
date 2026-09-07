package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Python-parity composition of a Trainer Feature's effect payloads.
 *
 * This is the reusable bridge between TrainerFeatureExecutionService's transactional
 * callback and TrainerFeatureEffectRegistry. Effects execute in declaration order. Only
 * applied effects contribute to the aggregate result, while target deduplication preserves
 * first occurrence order exactly like TrainerFeatureDispatcher._apply_feature().
 */
public final class TrainerFeatureEffectBatch {
    private TrainerFeatureEffectBatch() {}

    public record BatchResult(
            boolean applied,
            String effectType,
            List<String> effectTypes,
            List<String> targets,
            List<Map<String, Object>> details
    ) {
        public BatchResult {
            effectType = effectType == null ? "" : effectType;
            effectTypes = effectTypes == null ? List.of() : List.copyOf(effectTypes);
            targets = targets == null ? List.of() : List.copyOf(targets);
            if (details == null) {
                details = List.of();
            } else {
                ArrayList<Map<String, Object>> copy = new ArrayList<>();
                for (Map<String, Object> detail : details) {
                    copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(detail)));
                }
                details = List.copyOf(copy);
            }
        }
    }

    public static BatchResult apply(
            TrainerFeatureEffectRegistry registry,
            TrainerFeatureEffectRegistry.EffectContext context
    ) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(context, "context");
        ArrayList<TrainerFeatureEffectRegistry.EffectResult> results = new ArrayList<>();
        for (Map<String, Object> effect : effects(context.feature())) {
            results.add(registry.apply(context, effect));
        }
        return aggregate(results);
    }

    /** Mirrors the aggregation portion of Python TrainerFeatureDispatcher._apply_feature(). */
    public static BatchResult aggregate(List<TrainerFeatureEffectRegistry.EffectResult> results) {
        ArrayList<String> appliedTypes = new ArrayList<>();
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        ArrayList<Map<String, Object>> details = new ArrayList<>();
        if (results != null) {
            for (TrainerFeatureEffectRegistry.EffectResult result : results) {
                if (result == null || !result.applied()) continue;
                appliedTypes.add(result.effectType().isBlank() ? "log_only" : result.effectType());
                targets.addAll(result.targets());
                if (!result.details().isEmpty()) details.add(result.details());
            }
        }
        boolean applied = !appliedTypes.isEmpty();
        String aggregateType = !applied
                ? ""
                : (appliedTypes.size() == 1 ? appliedTypes.get(0) : "multi");
        return new BatchResult(applied, aggregateType, appliedTypes, new ArrayList<>(targets), details);
    }

    /** Mirrors Python TrainerFeatureDispatcher._feature_effects(). */
    public static List<Map<String, Object>> effects(Map<String, ?> feature) {
        Map<String, ?> safeFeature = feature == null ? Map.of() : feature;
        Object rawEffects = safeFeature.get("effects");
        if (rawEffects instanceof List<?> values) {
            List<Map<String, Object>> entries = mapEntries(values);
            return entries.isEmpty() ? List.of(Map.of()) : entries;
        }

        Object rawPrimary = safeFeature.get("effect_payload");
        if (rawPrimary == null) rawPrimary = safeFeature.get("effect");
        if (rawPrimary instanceof List<?> values) {
            List<Map<String, Object>> entries = mapEntries(values);
            return entries.isEmpty() ? List.of(Map.of()) : entries;
        }
        if (rawPrimary instanceof Map<?, ?> map) {
            return List.of(stringMap(map));
        }
        return List.of(Map.of());
    }

    private static List<Map<String, Object>> mapEntries(List<?> values) {
        ArrayList<Map<String, Object>> entries = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Map<?, ?> map) entries.add(stringMap(map));
        }
        return List.copyOf(entries);
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() instanceof String key) result.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }
}
