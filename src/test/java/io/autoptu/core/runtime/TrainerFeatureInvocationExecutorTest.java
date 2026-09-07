package io.autoptu.core.runtime;

import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.rules.TrainerFeatureExecutionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainerFeatureInvocationExecutorTest {
    @Test
    void composesDispatchExecutionAndEffectBatchInPythonOrder() {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(3, 3, Set.of(), Map.of()),
                List.of()
        );
        TrainerRuntimeState trainerB = new TrainerRuntimeState("trainer-b", List.of("Alpha"), 3);
        TrainerRuntimeState trainerA = new TrainerRuntimeState("trainer-a", List.of("Beta"), 3);
        state.putTrainer(trainerB);
        state.putTrainer(trainerA);

        TrainerFeatureDispatchCatalog.TrainerSpec specB = spec(
                "trainer-b",
                feature("alpha", "round_start", Map.of())
        );
        TrainerFeatureDispatchCatalog.TrainerSpec specA = spec(
                "trainer-a",
                feature("beta", "round_start", Map.of("conditions", Map.of("min_round", 4)))
        );

        List<TrainerFeatureInvocationExecutor.InvocationResult> results =
                TrainerFeatureInvocationExecutor.execute(
                        "round_start",
                        List.of(specB, specA),
                        state,
                        new TrainerFeatureEffectRegistry(),
                        TrainerFeatureInvocationExecutor.roundStartContext(3, Map.of("round", 3))
                );

        assertEquals(List.of("trainer-b", "trainer-a"),
                results.stream().map(result -> result.invocation().trainerId()).toList());

        TrainerFeatureInvocationExecutor.InvocationResult applied = results.get(0);
        assertTrue(applied.applied());
        assertEquals(TrainerFeatureExecutionService.Outcome.APPLIED, applied.execution().outcome());
        assertTrue(applied.effects().applied());
        assertEquals("log_only", applied.effects().effectType());
        assertTrue(trainerB.featureUsage().containsKey("alpha"));

        TrainerFeatureInvocationExecutor.InvocationResult blocked = results.get(1);
        assertFalse(blocked.applied());
        assertEquals(TrainerFeatureExecutionService.Outcome.CONTEXT_FAILED, blocked.execution().outcome());
        assertFalse(blocked.effects().applied());
        assertTrue(trainerA.featureUsage().isEmpty());
    }

    @Test
    void roundStartContextCarriesOnlyServerOwnedRoundPayload() {
        var invocation = new TrainerFeatureDispatchCatalog.BoundInvocation(
                "trainer-a", "alpha", "feature", Map.of(), Map.of(), List.of()
        );
        var context = TrainerFeatureInvocationExecutor.roundStartContext(7, null).contextFor(invocation);

        assertEquals("trainer-a", context.trainerId());
        assertEquals("", context.actorId());
        assertEquals("", context.actorTrainerId());
        assertFalse(context.actorIsPokemon());
        assertFalse(context.actorActive());
        assertEquals(7, context.currentRound());
        assertEquals("round_start", context.battlePhase());
        assertEquals(Map.of("round", 7), context.payload());
    }

    private static TrainerFeatureDispatchCatalog.TrainerSpec spec(
            String trainerId,
            TrainerFeatureDispatchCatalog.FeatureSpec feature
    ) {
        return new TrainerFeatureDispatchCatalog.TrainerSpec(
                trainerId,
                Map.of(),
                List.of(feature.dispatchEntry().featureId()),
                List.of(feature),
                List.of(),
                List.of()
        );
    }

    private static TrainerFeatureDispatchCatalog.FeatureSpec feature(
            String id,
            String trigger,
            Map<String, Object> extras
    ) {
        var entry = new TrainerFeatureEventDispatchPlan.FeatureEntry(id, id, trigger, true, "feature");
        java.util.LinkedHashMap<String, Object> definition = new java.util.LinkedHashMap<>();
        definition.put("feature_id", id);
        definition.put("trigger", trigger);
        definition.putAll(extras);
        return new TrainerFeatureDispatchCatalog.FeatureSpec(entry, definition);
    }
}
