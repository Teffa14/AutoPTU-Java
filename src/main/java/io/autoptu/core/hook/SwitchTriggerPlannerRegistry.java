package io.autoptu.core.hook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Registry for reusable Trainer Feature switch-trigger planners. */
public final class SwitchTriggerPlannerRegistry {
    private final Map<String, Planner> planners;

    public SwitchTriggerPlannerRegistry(Map<String, Planner> planners) {
        LinkedHashMap<String, Planner> copy = new LinkedHashMap<>();
        if (planners != null) {
            planners.forEach((featureName, planner) -> {
                String key = normalizeFeature(featureName);
                if (planner == null) throw new IllegalArgumentException("planner is required");
                copy.put(key, planner);
            });
        }
        this.planners = Collections.unmodifiableMap(copy);
    }

    public static SwitchTriggerPlannerRegistry empty() {
        return new SwitchTriggerPlannerRegistry(Map.of());
    }

    public SwitchTriggerPlannerRegistry withPlanner(String featureName, Planner planner) {
        if (planner == null) throw new IllegalArgumentException("planner is required");
        LinkedHashMap<String, Planner> next = new LinkedHashMap<>(planners);
        next.put(normalizeFeature(featureName), planner);
        return new SwitchTriggerPlannerRegistry(next);
    }

    /** Registered feature families in deterministic dispatch order. */
    public List<String> featureNames() {
        return List.copyOf(planners.keySet());
    }

    public List<SwitchTriggerDecisionPlan> plans(PlanningContext context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        ArrayList<SwitchTriggerDecisionPlan> results = new ArrayList<>();
        for (String featureName : planners.keySet()) {
            plansForFeature(featureName, context).ifPresent(results::add);
        }
        return List.copyOf(results);
    }

    /**
     * Plan exactly one registered Feature family.
     *
     * <p>This seam lets runtime dispatch apply Feature-specific pre-dispatch guards without
     * allowing one Feature's dedupe state to suppress unrelated planners that share the same
     * battle trigger.</p>
     */
    public Optional<SwitchTriggerDecisionPlan> plansForFeature(String featureName, PlanningContext context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        String key = normalizeFeature(featureName);
        Planner planner = planners.get(key);
        if (planner == null || !context.trainerFeatures().contains(key)) return Optional.empty();
        return planner.plan(context);
    }

    @FunctionalInterface
    public interface Planner {
        Optional<SwitchTriggerDecisionPlan> plan(PlanningContext context);
    }

    public record PlanningContext(
            SwitchTriggerDecisionPlan.Trigger trigger,
            String actorId,
            boolean actorActive,
            boolean actorFainted,
            int availableAp,
            List<String> replacementIds,
            Set<String> trainerFeatures,
            boolean triggerAlreadyHandled
    ) {
        public PlanningContext {
            if (trigger == null) throw new IllegalArgumentException("trigger is required");
            if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
            actorId = actorId.strip();
            if (availableAp < 0) throw new IllegalArgumentException("availableAp cannot be negative");
            replacementIds = replacementIds == null ? List.of() : replacementIds.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::strip)
                    .distinct()
                    .toList();
            trainerFeatures = trainerFeatures == null ? Set.of() : trainerFeatures.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(SwitchTriggerPlannerRegistry::normalizeFeature)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    private static String normalizeFeature(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("featureName is required");
        return value.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
