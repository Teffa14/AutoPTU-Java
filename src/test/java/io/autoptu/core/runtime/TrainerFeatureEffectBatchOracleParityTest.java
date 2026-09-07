package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainerFeatureEffectBatchOracleParityTest {
    @Test
    void matchesPinnedPythonExtractionAndAggregation() throws IOException {
        Path fixture = Path.of("build/oracle/trainer-feature-effect-batch.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> oracle = new LinkedHashMap<>();
        for (String line : Files.readAllLines(fixture)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            oracle.put(parts[0], parts[1]);
        }

        LinkedHashMap<String, Object> feature = new LinkedHashMap<>();
        feature.put("effects", List.of(
                Map.of("type", "skip"),
                "ignored-non-map",
                Map.of("type", "first"),
                Map.of("type", "second")
        ));
        feature.put("effect_payload", Map.of("type", "must-not-win"));
        assertEquals(oracle.get("extracted_types"), types(TrainerFeatureEffectBatch.effects(feature)));
        assertEquals("1", oracle.get("empty_fallback_log_only"));
        assertEquals(List.of(Map.of()), TrainerFeatureEffectBatch.effects(Map.of("effects", List.of("noise"))));
        assertEquals(
                oracle.get("payload_fallback_types"),
                types(TrainerFeatureEffectBatch.effects(Map.of("effect_payload", List.of(Map.of("type", "first"), "noise"))))
        );
        assertEquals(
                oracle.get("primary_fallback_types"),
                types(TrainerFeatureEffectBatch.effects(Map.of("effect", Map.of("type", "second"))))
        );

        TrainerFeatureEffectBatch.BatchResult result = TrainerFeatureEffectBatch.aggregate(List.of(
                new TrainerFeatureEffectRegistry.EffectResult(false, "skip", List.of("ignored"), Map.of("ignored", true)),
                new TrainerFeatureEffectRegistry.EffectResult(true, "first", List.of("a", "b"), Map.of("order", 1)),
                new TrainerFeatureEffectRegistry.EffectResult(true, "second", List.of("b", "c"), Map.of("order", 2))
        ));
        assertTrue(result.applied());
        assertEquals(oracle.get("event_effect_type"), result.effectType());
        assertEquals(oracle.get("event_effect_types"), String.join("|", result.effectTypes()));
        assertEquals(oracle.get("event_targets"), String.join("|", result.targets()));
        assertEquals(
                oracle.get("event_detail_orders"),
                result.details().stream().map(detail -> String.valueOf(detail.get("order"))).reduce((a, b) -> a + "|" + b).orElse("")
        );
        assertEquals("1", oracle.get("event_actor_defaults_to_trainer"));
    }

    @Test
    void noAppliedEffectsProducesFalseEmptyAggregate() {
        TrainerFeatureEffectBatch.BatchResult result = TrainerFeatureEffectBatch.aggregate(List.of(
                new TrainerFeatureEffectRegistry.EffectResult(false, "heal", List.of(), Map.of())
        ));
        assertEquals(false, result.applied());
        assertEquals("", result.effectType());
        assertEquals(List.of(), result.effectTypes());
        assertEquals(List.of(), result.targets());
        assertEquals(List.of(), result.details());
    }

    private static String types(List<Map<String, Object>> effects) {
        return effects.stream().map(effect -> String.valueOf(effect.getOrDefault("type", "")))
                .reduce((a, b) -> a + "|" + b).orElse("");
    }
}
