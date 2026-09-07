package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Server-owned binding between the Python-parity Trainer Feature dispatch plan and rich
 * rule content required by TrainerFeatureExecutionService.
 *
 * The catalog deliberately keeps feature metadata inside the authoritative Java core.
 * Minecraft/Cobblemon/Craftics adapters may identify a trainer or render resulting events,
 * but they do not supply Feature definitions while battle rules are resolving.
 */
public final class TrainerFeatureDispatchCatalog {
    private TrainerFeatureDispatchCatalog() {}

    public record FeatureSpec(
            TrainerFeatureEventDispatchPlan.FeatureEntry dispatchEntry,
            Map<String, Object> definition
    ) {
        public FeatureSpec {
            if (dispatchEntry == null) {
                throw new IllegalArgumentException("dispatchEntry is required");
            }
            definition = immutableMap(definition);
        }
    }

    public record TrainerSpec(
            String trainerId,
            Map<String, Object> trainerClass,
            Collection<String> knownFeatureIds,
            List<FeatureSpec> features,
            List<FeatureSpec> edges,
            List<FeatureSpec> knownFeatures
    ) {
        public TrainerSpec {
            if (trainerId == null || trainerId.isBlank()) {
                throw new IllegalArgumentException("trainerId is required");
            }
            trainerId = trainerId.strip();
            trainerClass = immutableMap(trainerClass);
            knownFeatureIds = knownFeatureIds == null ? List.of() : List.copyOf(knownFeatureIds);
            features = features == null ? List.of() : List.copyOf(features);
            edges = edges == null ? List.of() : List.copyOf(edges);
            knownFeatures = knownFeatures == null ? List.of() : List.copyOf(knownFeatures);
        }

        TrainerFeatureEventDispatchPlan.TrainerEntry dispatchEntry() {
            return new TrainerFeatureEventDispatchPlan.TrainerEntry(
                    trainerId,
                    entries(features),
                    entries(edges),
                    entries(knownFeatures)
            );
        }
    }

    /**
     * A planner invocation bound back to the exact first-win Python Feature definition.
     */
    public record BoundInvocation(
            String trainerId,
            String featureId,
            String runtimeKind,
            Map<String, Object> trainerClass,
            Map<String, Object> feature,
            List<String> knownFeatureIds
    ) {
        public BoundInvocation {
            trainerClass = immutableMap(trainerClass);
            feature = immutableMap(feature);
            knownFeatureIds = knownFeatureIds == null ? List.of() : List.copyOf(knownFeatureIds);
        }
    }

    public static List<BoundInvocation> resolve(String trigger, List<TrainerSpec> trainers) {
        if (trainers == null || trainers.isEmpty()) return List.of();

        ArrayList<TrainerFeatureEventDispatchPlan.TrainerEntry> dispatchTrainers = new ArrayList<>();
        LinkedHashMap<String, TrainerSpec> trainersById = new LinkedHashMap<>();
        LinkedHashMap<String, Map<String, FeatureSpec>> featuresByTrainer = new LinkedHashMap<>();

        for (TrainerSpec trainer : trainers) {
            if (trainer == null) continue;
            if (trainersById.putIfAbsent(trainer.trainerId(), trainer) != null) {
                throw new IllegalArgumentException("duplicate trainerId: " + trainer.trainerId());
            }
            dispatchTrainers.add(trainer.dispatchEntry());
            featuresByTrainer.put(trainer.trainerId(), firstWinFeatures(trainer));
        }

        ArrayList<BoundInvocation> bound = new ArrayList<>();
        for (TrainerFeatureEventDispatchPlan.Invocation invocation
                : TrainerFeatureEventDispatchPlan.resolve(trigger, dispatchTrainers)) {
            TrainerSpec trainer = trainersById.get(invocation.trainerId());
            FeatureSpec feature = featuresByTrainer.get(invocation.trainerId()).get(invocation.featureId());
            if (trainer == null || feature == null) {
                throw new IllegalStateException("dispatch plan produced an unbound Trainer Feature invocation");
            }
            bound.add(new BoundInvocation(
                    invocation.trainerId(),
                    invocation.featureId(),
                    invocation.runtimeKind(),
                    trainer.trainerClass(),
                    feature.definition(),
                    List.copyOf(trainer.knownFeatureIds())
            ));
        }
        return List.copyOf(bound);
    }

    private static Map<String, FeatureSpec> firstWinFeatures(TrainerSpec trainer) {
        LinkedHashMap<String, FeatureSpec> result = new LinkedHashMap<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        append(result, seen, trainer.features());
        append(result, seen, trainer.edges());
        append(result, seen, trainer.knownFeatures());
        return Collections.unmodifiableMap(result);
    }

    private static void append(
            Map<String, FeatureSpec> result,
            Collection<String> seen,
            List<FeatureSpec> specs
    ) {
        for (FeatureSpec spec : specs) {
            if (spec == null) continue;
            String id = spec.dispatchEntry().identifier();
            if (!seen.add(id)) continue;
            result.put(id, spec);
        }
    }

    private static List<TrainerFeatureEventDispatchPlan.FeatureEntry> entries(List<FeatureSpec> specs) {
        ArrayList<TrainerFeatureEventDispatchPlan.FeatureEntry> result = new ArrayList<>();
        for (FeatureSpec spec : specs) {
            if (spec != null) result.add(spec.dispatchEntry());
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> immutableMap(Map<String, ?> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (source != null) result.putAll(source);
        return Collections.unmodifiableMap(result);
    }
}
