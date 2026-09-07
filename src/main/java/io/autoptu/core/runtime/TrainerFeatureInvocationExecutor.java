package io.autoptu.core.runtime;

import io.autoptu.core.rules.TrainerFeatureContextResolution;
import io.autoptu.core.rules.TrainerFeatureExecutionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server-authoritative composition of the Python-parity Trainer Feature pipeline.
 *
 * Traversal/content binding stays in {@link TrainerFeatureDispatchCatalog}; transactional
 * gates stay in {@link TrainerFeatureExecutionService}; concrete payload families stay in
 * {@link TrainerFeatureEffectRegistry}. This class only composes those frozen contracts in
 * invocation order so lifecycle hooks do not grow per-Feature branches.
 */
public final class TrainerFeatureInvocationExecutor {
    private TrainerFeatureInvocationExecutor() {}

    @FunctionalInterface
    public interface ContextFactory {
        TrainerFeatureContextResolution.Context contextFor(TrainerFeatureDispatchCatalog.BoundInvocation invocation);
    }

    public record InvocationResult(
            TrainerFeatureDispatchCatalog.BoundInvocation invocation,
            TrainerFeatureExecutionService.Result execution,
            TrainerFeatureEffectBatch.BatchResult effects
    ) {
        public InvocationResult {
            Objects.requireNonNull(invocation, "invocation");
            Objects.requireNonNull(execution, "execution");
            effects = effects == null
                    ? new TrainerFeatureEffectBatch.BatchResult(false, "", List.of(), List.of(), List.of())
                    : effects;
        }

        public boolean applied() {
            return execution.applied();
        }
    }

    public static List<InvocationResult> execute(
            String trigger,
            List<TrainerFeatureDispatchCatalog.TrainerSpec> trainers,
            BattleRuntimeState state,
            TrainerFeatureEffectRegistry effectRegistry,
            ContextFactory contextFactory
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(effectRegistry, "effectRegistry");
        Objects.requireNonNull(contextFactory, "contextFactory");

        ArrayList<InvocationResult> results = new ArrayList<>();
        for (TrainerFeatureDispatchCatalog.BoundInvocation invocation
                : TrainerFeatureDispatchCatalog.resolve(trigger, trainers)) {
            TrainerRuntimeState trainer = state.requireTrainer(invocation.trainerId());
            TrainerFeatureContextResolution.Context context = Objects.requireNonNull(
                    contextFactory.contextFor(invocation),
                    "Trainer Feature context factory returned null"
            );
            TrainerFeatureEffectBatch.BatchResult[] batch = new TrainerFeatureEffectBatch.BatchResult[1];
            TrainerFeatureExecutionService.Result execution = TrainerFeatureExecutionService.executeAuthoritative(
                    trigger,
                    invocation.trainerClass(),
                    invocation.feature(),
                    invocation.knownFeatureIds(),
                    context,
                    trainer,
                    () -> {
                        TrainerFeatureEffectRegistry.EffectContext effectContext =
                                new TrainerFeatureEffectRegistry.EffectContext(
                                        state,
                                        invocation.trainerId(),
                                        context.actorId(),
                                        invocation.feature(),
                                        context.payload()
                                );
                        batch[0] = TrainerFeatureEffectBatch.apply(effectRegistry, effectContext);
                        return batch[0].applied();
                    }
            );
            results.add(new InvocationResult(invocation, execution, batch[0]));
        }
        return List.copyOf(results);
    }

    /** Round-start context frozen by the existing Python dispatch contract: no actor/target. */
    public static ContextFactory roundStartContext(int round, Map<String, ?> payload) {
        if (round < 1) throw new IllegalArgumentException("round must be positive");
        Map<String, ?> safePayload = payload == null ? Map.of("round", round) : Map.copyOf(payload);
        return invocation -> new TrainerFeatureContextResolution.Context(
                invocation.trainerId(),
                "",
                "",
                false,
                false,
                round,
                "round_start",
                safePayload,
                Map.of(),
                null
        );
    }
}
