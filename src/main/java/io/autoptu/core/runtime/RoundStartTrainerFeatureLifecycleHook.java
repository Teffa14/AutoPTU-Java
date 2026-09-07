package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.hook.LifecycleHook;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Python-parity round-start bridge for declarative Trainer Features.
 *
 * <p>The hook owns no Feature-specific rules. It delegates traversal/content binding to
 * {@link TrainerFeatureDispatchCatalog}, transactional gates to the execution service composed by
 * {@link TrainerFeatureInvocationExecutor}, and concrete payload families to
 * {@link TrainerFeatureEffectRegistry}. This keeps the lifecycle seam server-authoritative while
 * preserving the Python ordering boundary: round-start snapshot, Trainer Features, then abilities.</p>
 */
public final class RoundStartTrainerFeatureLifecycleHook implements LifecycleHook {
    private final List<TrainerFeatureDispatchCatalog.TrainerSpec> trainers;
    private final TrainerFeatureEffectRegistry effectRegistry;

    public RoundStartTrainerFeatureLifecycleHook(
            List<TrainerFeatureDispatchCatalog.TrainerSpec> trainers,
            TrainerFeatureEffectRegistry effectRegistry
    ) {
        this.trainers = trainers == null ? List.of() : List.copyOf(trainers);
        this.effectRegistry = Objects.requireNonNull(effectRegistry, "effectRegistry");
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        Objects.requireNonNull(context, "context");
        if (context.hookPoint() != LifecycleHookPoint.ROUND_START_EFFECTS) {
            throw new IllegalArgumentException("RoundStartTrainerFeatureLifecycleHook requires ROUND_START_EFFECTS");
        }
        if (trainers.isEmpty()) return LifecycleHookResult.empty();

        Map<String, Object> payload = Map.of("round", context.round());
        List<TrainerFeatureInvocationExecutor.InvocationResult> results =
                TrainerFeatureInvocationExecutor.execute(
                        "round_start",
                        trainers,
                        context.state(),
                        effectRegistry,
                        TrainerFeatureInvocationExecutor.roundStartContext(context.round(), payload)
                );

        ArrayList<BattleEvent> events = new ArrayList<>();
        for (TrainerFeatureInvocationExecutor.InvocationResult result : results) {
            if (!result.applied()) continue;
            events.add(eventFor(result, context.round()));
        }
        return LifecycleHookResult.events(events);
    }

    private static TrainerFeatureEvent eventFor(
            TrainerFeatureInvocationExecutor.InvocationResult result,
            int round
    ) {
        TrainerFeatureDispatchCatalog.BoundInvocation invocation = result.invocation();
        Map<String, Object> feature = invocation.feature();
        String featureName = text(feature.get("name"));
        if (featureName.isBlank()) featureName = invocation.featureId();

        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("trainer", invocation.trainerId());
        details.put("phase", "round_start");
        details.put("trigger", "round_start");
        details.put("feature_id", invocation.featureId());
        details.put("feature_kind", invocation.runtimeKind());
        details.put("round", round);
        details.put("effect_types", result.effects().effectTypes());
        details.put("targets", result.effects().targets());

        return new TrainerFeatureEvent(
                invocation.trainerId(),
                featureName,
                result.effects().effectType(),
                details
        );
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }
}
