package io.autoptu.core.runtime;

import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.hook.BuiltinLifecycleHooks;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.model.MovementGrid;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundStartTrainerFeatureLifecycleOracleParityTest {
    @Test
    void matchesPinnedPythonAppliedEventAndFinalBookkeeping() throws IOException {
        Path fixture = Path.of("build/oracle/round-start-trainer-feature-lifecycle.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> oracle = new LinkedHashMap<>();
        for (String line : Files.readAllLines(fixture)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            oracle.put(parts[0], parts[1]);
        }

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(3, 3, Set.of(), Map.of()),
                List.of()
        );
        TrainerRuntimeState trainerB = new TrainerRuntimeState(
                "trainer-b", List.of("Alpha"), 3, 0, Map.of(), null, "",
                Map.of("focus", 2), Map.of()
        );
        TrainerRuntimeState trainerA = new TrainerRuntimeState(
                "trainer-a", List.of("Beta"), 3, 0, Map.of(), null, "",
                Map.of(), Map.of()
        );
        state.putTrainer(trainerB);
        state.putTrainer(trainerA);
        state.syncCurrentRoundFromLifecycle(3);

        var alpha = feature(
                "alpha", "Alpha",
                Map.of("resource_cost", Map.of("focus", 1))
        );
        var beta = feature(
                "beta", "Beta",
                Map.of("conditions", Map.of("min_round", 4))
        );
        var hooks = BuiltinLifecycleHooks.registry(
                new HeldItemRuleCatalog(Map.of()),
                List.of(spec("trainer-b", alpha), spec("trainer-a", beta)),
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

        assertEquals(oracle.get("event_count"), Integer.toString(result.events().size()));
        TrainerFeatureEvent event = (TrainerFeatureEvent) result.events().get(0);
        assertEquals(oracle.get("event_trainer"), event.trainer());
        assertEquals(oracle.get("event_actor"), event.actorId());
        assertEquals(oracle.get("event_feature_id"), String.valueOf(event.details().get("feature_id")));
        assertEquals(oracle.get("event_feature"), event.feature());
        assertEquals(oracle.get("event_feature_kind"), String.valueOf(event.details().get("feature_kind")));
        assertEquals(oracle.get("event_trigger"), String.valueOf(event.details().get("trigger")));
        assertEquals(oracle.get("event_effect_type"), event.effect());
        assertEquals(oracle.get("event_effect_types"), join(event.details().get("effect_types")));
        assertEquals(oracle.get("event_targets"), join(event.details().get("targets")));
        assertEquals(oracle.get("event_payload_round"), String.valueOf(event.details().get("round")));

        assertEquals(oracle.get("resource_focus"), String.valueOf(trainerB.featureResources().get("focus")));
        Map<String, Object> alphaUsage = trainerB.featureUsage().get("alpha");
        assertEquals(oracle.get("alpha_uses_total"), String.valueOf(alphaUsage.get("uses_total")));
        assertEquals(oracle.get("alpha_last_round"), String.valueOf(alphaUsage.get("last_round")));
        assertEquals(oracle.get("alpha_uses_round"), String.valueOf(alphaUsage.get("uses_round_3")));
        assertEquals(oracle.get("beta_usage_count"), Integer.toString(trainerA.featureUsage().size()));
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

    private static String join(Object value) {
        if (!(value instanceof List<?> values)) return "";
        return values.stream().map(String::valueOf).reduce((a, b) -> a + "|" + b).orElse("");
    }
}
