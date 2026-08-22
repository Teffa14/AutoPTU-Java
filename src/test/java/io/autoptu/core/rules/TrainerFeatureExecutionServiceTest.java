package io.autoptu.core.rules;

import io.autoptu.core.runtime.TrainerRuntimeState;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrainerFeatureExecutionServiceTest {
    @Test
    void pythonTriggerCommitsOnlyAfterAppliedEffect() throws IOException {
        Path fixture = Path.of("build/oracle/trainer-feature-execution-contract.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, Integer> contract = new LinkedHashMap<>();
        for (String line : Files.readAllLines(fixture)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            contract.put(parts[0], Integer.parseInt(parts[1]));
        }
        assertEquals(1, contract.get("apply_before_consume"));
        assertEquals(1, contract.get("consume_before_mark"));
        assertEquals(1, contract.get("consume_guarded_by_applied"));
        assertEquals(1, contract.get("mark_guarded_by_applied"));
    }

    @Test
    void successfulEffectCommitsResourcesAndUsageAfterEffect() {
        AtomicInteger effects = new AtomicInteger();
        TrainerFeatureExecutionService.Result result = TrainerFeatureExecutionService.execute(
                "round_start",
                Map.of("level", 5),
                feature(),
                List.of(),
                context(3),
                Map.of("focus", 5, "other", 7),
                Map.of(),
                () -> {
                    effects.incrementAndGet();
                    return true;
                }
        );

        assertEquals(TrainerFeatureExecutionService.Outcome.APPLIED, result.outcome());
        assertTrue(result.applied());
        assertEquals(1, effects.get());
        assertEquals(Map.of("focus", 3, "other", 7), result.resources());
        assertEquals(1, result.usage().get("tactical-burst").get("uses_total"));
        assertEquals(1, result.usage().get("tactical-burst").get("uses_round_3"));
        assertEquals(3, result.usage().get("tactical-burst").get("last_round"));
        assertEquals(4, result.usage().get("tactical-burst").get("cooldown_until"));
    }

    @Test
    void authoritativeExecutionCommitsOnlyToTrainerRuntimeState() {
        TrainerRuntimeState trainer = trainerState(
                Map.of("focus", 5, "other", 7),
                Map.of("tactical-burst", Map.of("legacy", 9))
        );
        TrainerFeatureExecutionService.Result result = TrainerFeatureExecutionService.executeAuthoritative(
                "round_start",
                Map.of("level", 5),
                feature(),
                List.of(),
                context(3),
                trainer,
                () -> true
        );

        assertEquals(TrainerFeatureExecutionService.Outcome.APPLIED, result.outcome());
        assertEquals(Map.of("focus", 3, "other", 7), trainer.featureResources());
        assertEquals(9, trainer.featureUsage().get("tactical-burst").get("legacy"));
        assertEquals(1, trainer.featureUsage().get("tactical-burst").get("uses_total"));
        assertEquals(1, trainer.featureUsage().get("tactical-burst").get("uses_round_3"));
        assertEquals(4, trainer.featureUsage().get("tactical-burst").get("cooldown_until"));
    }

    @Test
    void authoritativeExecutionLeavesRuntimeStateUntouchedWhenEffectDoesNotApply() {
        TrainerRuntimeState trainer = trainerState(
                Map.of("focus", 5),
                Map.of("tactical-burst", Map.of("legacy", 9))
        );
        Map<String, Object> beforeResources = trainer.featureResources();
        Map<String, Map<String, Object>> beforeUsage = trainer.featureUsage();

        TrainerFeatureExecutionService.Result result = TrainerFeatureExecutionService.executeAuthoritative(
                "round_start",
                Map.of("level", 5),
                feature(),
                List.of(),
                context(3),
                trainer,
                () -> false
        );

        assertEquals(TrainerFeatureExecutionService.Outcome.EFFECT_NOT_APPLIED, result.outcome());
        assertEquals(beforeResources, trainer.featureResources());
        assertEquals(beforeUsage, trainer.featureUsage());
    }

    @Test
    void authoritativeExecutionUsesCanonicalTrainerIdentityForContextScope() {
        LinkedHashMap<String, Object> scoped = new LinkedHashMap<>(feature());
        scoped.put("conditions", Map.of("actor_scope", "self"));
        TrainerRuntimeState trainer = trainerState(Map.of("focus", 5), Map.of());
        TrainerFeatureContextResolution.Context forgedTrainerContext = new TrainerFeatureContextResolution.Context(
                "forged-trainer", "pokemon-1", "trainer-1", true, true, 3,
                "START", Map.of("phase", "START"), Map.of(), null
        );

        TrainerFeatureExecutionService.Result result = TrainerFeatureExecutionService.executeAuthoritative(
                "round_start",
                Map.of("level", 5),
                scoped,
                List.of(),
                forgedTrainerContext,
                trainer,
                () -> true
        );

        assertTrue(result.applied());
        assertEquals(3, trainer.featureResources().get("focus"));
    }

    @Test
    void trainerFeatureBookkeepingSnapshotsAreDefensiveAndAtomic() {
        LinkedHashMap<String, Object> resources = new LinkedHashMap<>();
        resources.put("focus", 5);
        LinkedHashMap<String, Object> usageInfo = new LinkedHashMap<>();
        usageInfo.put("uses_total", 1);
        LinkedHashMap<String, Map<String, Object>> usage = new LinkedHashMap<>();
        usage.put("tactical-burst", usageInfo);
        TrainerRuntimeState trainer = trainerState(resources, usage);

        resources.put("focus", 999);
        usageInfo.put("uses_total", 999);
        assertEquals(5, trainer.featureResources().get("focus"));
        assertEquals(1, trainer.featureUsage().get("tactical-burst").get("uses_total"));
        assertThrows(UnsupportedOperationException.class, () -> trainer.featureResources().put("focus", 0));
        assertThrows(
                UnsupportedOperationException.class,
                () -> trainer.featureUsage().get("tactical-burst").put("uses_total", 0)
        );
    }

    @Test
    void effectThatDoesNotApplyLeavesAllBookkeepingUntouched() {
        AtomicInteger effects = new AtomicInteger();
        Map<String, Map<String, Object>> usage = Map.of(
                "tactical-burst",
                Map.of("legacy", 9)
        );
        TrainerFeatureExecutionService.Result result = TrainerFeatureExecutionService.execute(
                "round_start",
                Map.of("level", 5),
                feature(),
                List.of(),
                context(3),
                Map.of("focus", 5),
                usage,
                () -> {
                    effects.incrementAndGet();
                    return false;
                }
        );

        assertEquals(TrainerFeatureExecutionService.Outcome.EFFECT_NOT_APPLIED, result.outcome());
        assertFalse(result.applied());
        assertEquals(1, effects.get());
        assertEquals(Map.of("focus", 5), result.resources());
        assertEquals(usage, result.usage());
    }

    @Test
    void blockedGuardsNeverRunEffectOrMutateState() {
        assertBlocked(
                TrainerFeatureExecutionService.Outcome.RESOURCES_BLOCKED,
                feature(),
                Map.of("level", 5),
                Map.of("focus", 1),
                Map.of()
        );
        assertBlocked(
                TrainerFeatureExecutionService.Outcome.FREQUENCY_BLOCKED,
                feature(),
                Map.of("level", 5),
                Map.of("focus", 5),
                Map.of("tactical-burst", Map.of("uses_total", 2))
        );
        LinkedHashMap<String, Object> gated = new LinkedHashMap<>(feature());
        gated.put("min_trainer_level", 6);
        assertBlocked(
                TrainerFeatureExecutionService.Outcome.PREREQUISITES_FAILED,
                gated,
                Map.of("level", 5),
                Map.of("focus", 5),
                Map.of()
        );
    }

    @Test
    void disabledAndWrongTriggerFailBeforeEffect() {
        AtomicInteger effects = new AtomicInteger();
        LinkedHashMap<String, Object> disabled = new LinkedHashMap<>(feature());
        disabled.put("enabled", false);
        TrainerFeatureExecutionService.Result disabledResult = TrainerFeatureExecutionService.execute(
                "round_start", Map.of("level", 5), disabled, List.of(), context(3),
                Map.of("focus", 5), Map.of(), () -> { effects.incrementAndGet(); return true; }
        );
        assertEquals(TrainerFeatureExecutionService.Outcome.DISABLED, disabledResult.outcome());

        TrainerFeatureExecutionService.Result triggerResult = TrainerFeatureExecutionService.execute(
                "turn_start", Map.of("level", 5), feature(), List.of(), context(3),
                Map.of("focus", 5), Map.of(), () -> { effects.incrementAndGet(); return true; }
        );
        assertEquals(TrainerFeatureExecutionService.Outcome.TRIGGER_MISMATCH, triggerResult.outcome());
        assertEquals(0, effects.get());
    }

    private static void assertBlocked(
            TrainerFeatureExecutionService.Outcome expected,
            Map<String, ?> feature,
            Map<String, ?> trainerClass,
            Map<String, ?> resources,
            Map<String, ? extends Map<String, ?>> usage
    ) {
        AtomicInteger effects = new AtomicInteger();
        TrainerFeatureExecutionService.Result result = TrainerFeatureExecutionService.execute(
                "round_start", trainerClass, feature, List.of(), context(3), resources, usage,
                () -> { effects.incrementAndGet(); return true; }
        );
        assertEquals(expected, result.outcome());
        assertEquals(0, effects.get());
        assertEquals(resources, result.resources());
        assertEquals(usage, result.usage());
    }

    private static Map<String, Object> feature() {
        LinkedHashMap<String, Object> feature = new LinkedHashMap<>();
        feature.put("name", "Tactical Burst");
        feature.put("trigger", "round_start");
        feature.put("resource_cost", Map.of("focus", 2));
        feature.put("frequency", "2/scene");
        feature.put("cooldown_rounds", 1);
        return feature;
    }

    private static TrainerFeatureContextResolution.Context context(int round) {
        return new TrainerFeatureContextResolution.Context(
                "trainer-1", "", "", false, false, round, "START", Map.of("phase", "START"), Map.of(), null
        );
    }

    private static TrainerRuntimeState trainerState(
            Map<String, ?> resources,
            Map<String, ? extends Map<String, ?>> usage
    ) {
        return new TrainerRuntimeState(
                "trainer-1", List.of("Tactical Burst"), 3, 0, Map.of(), null, "team-a",
                resources, usage
        );
    }
}
