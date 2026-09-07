package io.autoptu.core.runtime;

import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.hook.BuiltinLifecycleHooks;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.model.MovementGrid;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundStartTrainerFeatureLifecycleHookTest {
    @Test
    void builtinRoundStartEffectsExecutesFeaturesInPythonOrderAndEmitsAppliedEventsOnly() {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(3, 3, Set.of(), Map.of()),
                List.of()
        );
        TrainerRuntimeState trainerB = new TrainerRuntimeState("trainer-b", List.of("Alpha"), 3);
        TrainerRuntimeState trainerA = new TrainerRuntimeState("trainer-a", List.of("Beta"), 3);
        state.putTrainer(trainerB);
        state.putTrainer(trainerA);
        state.syncCurrentRoundFromLifecycle(3);

        var alpha = feature("alpha", "Alpha", Map.of());
        var beta = feature("beta", "Beta", Map.of("conditions", Map.of("min_round", 4)));
        List<TrainerFeatureDispatchCatalog.TrainerSpec> specs = List.of(
                spec("trainer-b", alpha),
                spec("trainer-a", beta)
        );

        var hooks = BuiltinLifecycleHooks.registry(
                new HeldItemRuleCatalog(Map.of()),
                specs,
                new TrainerFeatureEffectRegistry()
        );
        var result = hooks.resolve(
                LifecycleHookPoint.ROUND_START_EFFECTS,
                new LifecycleHookContext(
                        state,
                        new RoundDamageHistoryState(),
                        new RoundInjuryHistoryState(),
                        LifecycleHookPoint.ROUND_START_EFFECTS,
                        2,
                        3,
                        ""
                )
        );

        assertEquals(1, result.events().size());
        TrainerFeatureEvent event = (TrainerFeatureEvent) result.events().get(0);
        assertEquals("trainer-b", event.actorId());
        assertEquals("Alpha", event.feature());
        assertEquals("log_only", event.effect());
        assertEquals("trainer-b", event.trainer());
        assertEquals("round_start", event.phase());
        assertEquals("alpha", event.details().get("feature_id"));
        assertEquals("feature", event.details().get("feature_kind"));
        assertEquals("round_start", event.details().get("trigger"));
        assertEquals(3, event.details().get("round"));
        assertEquals(List.of("log_only"), event.details().get("effect_types"));
        assertEquals(List.of(), event.details().get("targets"));

        assertTrue(trainerB.featureUsage().containsKey("alpha"));
        assertFalse(trainerA.featureUsage().containsKey("beta"));
    }

    @Test
    void emptyCanonicalContentKeepsDefaultRoundStartEffectsCompatibilitySafe() {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(2, 2, Set.of(), Map.of()),
                List.of()
        );

        var result = BuiltinLifecycleHooks.registry().resolve(
                LifecycleHookPoint.ROUND_START_EFFECTS,
                new LifecycleHookContext(
                        state,
                        new RoundDamageHistoryState(),
                        new RoundInjuryHistoryState(),
                        LifecycleHookPoint.ROUND_START_EFFECTS,
                        0,
                        1,
                        ""
                )
        );

        assertTrue(result.events().isEmpty());
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
            String name,
            Map<String, Object> extras
    ) {
        var entry = new TrainerFeatureEventDispatchPlan.FeatureEntry(id, name, "round_start", true, "feature");
        LinkedHashMap<String, Object> definition = new LinkedHashMap<>();
        definition.put("feature_id", id);
        definition.put("name", name);
        definition.put("trigger", "round_start");
        definition.putAll(extras);
        return new TrainerFeatureDispatchCatalog.FeatureSpec(entry, definition);
    }
}
