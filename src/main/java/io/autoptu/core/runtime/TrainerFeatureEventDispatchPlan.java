package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Language-neutral traversal contract frozen from Python TrainerFeatureDispatcher.trigger().
 *
 * This class decides only which Trainer Feature hooks are invoked and in what order. Concrete
 * effect execution remains behind the existing server-owned Trainer Feature execution/effect
 * infrastructure. Keeping traversal separate prevents lifecycle controllers and adapters from
 * reimplementing Python ordering or event-filter rules.
 */
public final class TrainerFeatureEventDispatchPlan {
    private TrainerFeatureEventDispatchPlan() {}

    public record TrainerEntry(
            String trainerId,
            String side,
            int index,
            String trainerName,
            List<String> features
    ) {
        public TrainerEntry {
            if (trainerId == null || trainerId.isBlank()) {
                throw new IllegalArgumentException("trainerId is required");
            }
            trainerId = trainerId.strip();
            side = side == null ? "" : side;
            trainerName = trainerName == null ? "" : trainerName;
            features = features == null ? List.of() : List.copyOf(features);
        }
    }

    public record HookMetadata(String event, Set<String> events) {
        public HookMetadata {
            event = event == null ? "" : event;
            events = events == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(events));
        }

        boolean allows(String trigger) {
            if (!event.isBlank() && !event.equals(trigger)) return false;
            return events.isEmpty() || events.contains(trigger);
        }
    }

    public record Invocation(String trainerId, String featureName) {}

    public static List<Invocation> resolve(
            String trigger,
            Collection<TrainerEntry> trainers,
            Collection<String> registeredHooks,
            Map<String, HookMetadata> metadataByFeature
    ) {
        if (trigger == null || trigger.isBlank()) return List.of();

        LinkedHashSet<String> hookNames = new LinkedHashSet<>();
        if (registeredHooks != null) {
            for (String hook : registeredHooks) {
                String normalized = normalize(hook);
                if (!normalized.isBlank()) hookNames.add(normalized);
            }
        }

        LinkedHashMap<String, HookMetadata> metadata = new LinkedHashMap<>();
        if (metadataByFeature != null) {
            for (Map.Entry<String, HookMetadata> entry : metadataByFeature.entrySet()) {
                String normalized = normalize(entry.getKey());
                if (!normalized.isBlank() && entry.getValue() != null) {
                    metadata.put(normalized, entry.getValue());
                }
            }
        }

        ArrayList<TrainerEntry> ordered = new ArrayList<>();
        if (trainers != null) ordered.addAll(trainers);
        ordered.sort(Comparator
                .comparing((TrainerEntry trainer) -> trainer.side() == null ? "" : trainer.side())
                .thenComparingInt(TrainerEntry::index)
                .thenComparing(trainer -> trainer.trainerName() == null ? "" : trainer.trainerName()));

        ArrayList<Invocation> invocations = new ArrayList<>();
        for (TrainerEntry trainer : ordered) {
            for (String rawFeature : trainer.features()) {
                if (rawFeature == null) continue;
                String featureName = rawFeature.strip();
                if (featureName.isBlank()) continue;
                String key = normalize(featureName);
                if (!hookNames.contains(key)) continue;
                HookMetadata hookMetadata = metadata.get(key);
                if (hookMetadata != null && !hookMetadata.allows(trigger)) continue;
                invocations.add(new Invocation(trainer.trainerId(), featureName));
            }
        }
        return List.copyOf(invocations);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
