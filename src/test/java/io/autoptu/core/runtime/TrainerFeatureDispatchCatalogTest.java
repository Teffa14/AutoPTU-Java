package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrainerFeatureDispatchCatalogTest {
    private static TrainerFeatureDispatchCatalog.FeatureSpec feature(
            String id,
            String name,
            String trigger,
            Boolean enabled,
            String runtimeKind,
            String marker
    ) {
        return new TrainerFeatureDispatchCatalog.FeatureSpec(
                new TrainerFeatureEventDispatchPlan.FeatureEntry(id, name, trigger, enabled, runtimeKind),
                Map.of("feature_id", id, "name", name, "trigger", trigger, "marker", marker)
        );
    }

    @Test
    void bindsPlannerOrderToExactFirstWinDefinitions() {
        var firstFeature = feature("duplicate", "First", "round_start", true, "", "feature-first");
        var duplicateEdge = feature("duplicate", "Later", "round_start", true, "edge", "edge-later");
        var edgeHit = feature("edge-hit", "Edge Hit", "round_start", true, "edge", "edge-hit");
        var knownHit = feature("known-hit", "Known Hit", " ROUND_START ", true, "", "known-hit");
        var ignored = feature("turn-only", "Turn Only", "turn_start", true, "", "ignored");

        var trainerB = new TrainerFeatureDispatchCatalog.TrainerSpec(
                "trainer-b",
                Map.of("class_id", "ace"),
                List.of("duplicate", "edge-hit", "known-hit"),
                List.of(firstFeature, ignored),
                List.of(duplicateEdge, edgeHit),
                List.of(knownHit)
        );
        var trainerA = new TrainerFeatureDispatchCatalog.TrainerSpec(
                "trainer-a",
                Map.of("class_id", "mentor"),
                List.of("named-feature"),
                List.of(feature("Named Feature", "", "ROUND_START", true, "", "named")),
                List.of(),
                List.of()
        );

        var result = TrainerFeatureDispatchCatalog.resolve(" Round_Start ", List.of(trainerB, trainerA));

        assertEquals(List.of("duplicate", "edge-hit", "known-hit", "named-feature"),
                result.stream().map(TrainerFeatureDispatchCatalog.BoundInvocation::featureId).toList());
        assertEquals(List.of("feature", "edge", "feature", "feature"),
                result.stream().map(TrainerFeatureDispatchCatalog.BoundInvocation::runtimeKind).toList());
        assertEquals("feature-first", result.get(0).feature().get("marker"));
        assertEquals("edge-hit", result.get(1).feature().get("marker"));
        assertEquals("ace", result.get(0).trainerClass().get("class_id"));
        assertEquals(List.of("duplicate", "edge-hit", "known-hit"), result.get(0).knownFeatureIds());
    }

    @Test
    void rejectsDuplicateTrainerIdentityInsteadOfAmbiguousBinding() {
        var one = new TrainerFeatureDispatchCatalog.TrainerSpec(
                "trainer", Map.of(), List.of(), List.of(), List.of(), List.of());
        var two = new TrainerFeatureDispatchCatalog.TrainerSpec(
                "trainer", Map.of(), List.of(), List.of(), List.of(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> TrainerFeatureDispatchCatalog.resolve("round_start", List.of(one, two)));
    }

    @Test
    void blankOrUnmatchedTriggerProducesNoBoundInvocation() {
        var trainer = new TrainerFeatureDispatchCatalog.TrainerSpec(
                "trainer",
                Map.of(),
                List.of("alpha"),
                List.of(feature("alpha", "Alpha", "round_start", true, "", "alpha")),
                List.of(),
                List.of()
        );
        assertEquals(List.of(), TrainerFeatureDispatchCatalog.resolve("", List.of(trainer)));
        assertEquals(List.of(), TrainerFeatureDispatchCatalog.resolve("turn_end", List.of(trainer)));
    }
}
