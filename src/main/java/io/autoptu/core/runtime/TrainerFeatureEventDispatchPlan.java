package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Language-neutral traversal contract frozen from Python TrainerFeatureDispatcher.trigger().
 *
 * The planner mirrors only collection, de-duplication, enabled filtering, trigger normalization,
 * and invocation order. Prerequisite, context, frequency, resource, and effect handling remain in
 * the server-owned Trainer Feature execution infrastructure.
 */
public final class TrainerFeatureEventDispatchPlan {
    private TrainerFeatureEventDispatchPlan() {}

    public record FeatureEntry(String featureId, String name, String trigger, Boolean enabled, String runtimeKind) {
        public FeatureEntry {
            featureId = featureId == null ? "" : featureId;
            name = name == null ? "" : name;
            trigger = trigger == null ? "" : trigger;
            runtimeKind = runtimeKind == null ? "" : runtimeKind;
        }

        String identifier() {
            String raw = !featureId.isBlank() ? featureId : name;
            String token = normalize(raw).replace(" ", "-");
            return token.isBlank() ? "feature" : token;
        }

        boolean isEnabled() {
            return enabled == null || enabled;
        }

        String normalizedTrigger() {
            return normalize(trigger);
        }
    }

    public record TrainerEntry(
            String trainerId,
            List<FeatureEntry> features,
            List<FeatureEntry> edges,
            List<FeatureEntry> knownFeatures
    ) {
        public TrainerEntry {
            if (trainerId == null || trainerId.isBlank()) {
                throw new IllegalArgumentException("trainerId is required");
            }
            trainerId = trainerId.strip();
            features = features == null ? List.of() : List.copyOf(features);
            edges = edges == null ? List.of() : List.copyOf(edges);
            knownFeatures = knownFeatures == null ? List.of() : List.copyOf(knownFeatures);
        }
    }

    public record Invocation(String trainerId, String featureId, String runtimeKind) {}

    public static List<Invocation> resolve(String trigger, List<TrainerEntry> trainers) {
        String token = normalize(trigger);
        if (token.isBlank() || trainers == null || trainers.isEmpty()) return List.of();

        ArrayList<Invocation> invocations = new ArrayList<>();
        for (TrainerEntry trainer : trainers) {
            for (FeatureEntry feature : collectFeatures(trainer)) {
                if (!feature.isEnabled()) continue;
                if (!feature.normalizedTrigger().equals(token)) continue;
                invocations.add(new Invocation(trainer.trainerId(), feature.identifier(), runtimeKind(feature)));
            }
        }
        return List.copyOf(invocations);
    }

    private static List<FeatureEntry> collectFeatures(TrainerEntry trainer) {
        ArrayList<FeatureEntry> collected = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        append(collected, seen, trainer.features(), "feature");
        append(collected, seen, trainer.edges(), "edge");
        append(collected, seen, trainer.knownFeatures(), "feature");
        return collected;
    }

    private static void append(
            List<FeatureEntry> collected,
            LinkedHashSet<String> seen,
            List<FeatureEntry> entries,
            String defaultRuntimeKind
    ) {
        for (FeatureEntry feature : entries) {
            if (feature == null) continue;
            String id = feature.identifier();
            if (!seen.add(id)) continue;
            String kind = feature.runtimeKind().isBlank() ? defaultRuntimeKind : normalize(feature.runtimeKind());
            collected.add(new FeatureEntry(
                    feature.featureId(), feature.name(), feature.trigger(), feature.enabled(), kind));
        }
    }

    private static String runtimeKind(FeatureEntry feature) {
        return feature.runtimeKind().isBlank() ? "feature" : normalize(feature.runtimeKind());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
