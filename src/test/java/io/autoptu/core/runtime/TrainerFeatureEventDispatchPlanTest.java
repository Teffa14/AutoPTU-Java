package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainerFeatureEventDispatchPlanTest {
    private static TrainerFeatureEventDispatchPlan.FeatureEntry feature(
            String id, String name, String trigger, Boolean enabled, String kind
    ) {
        return new TrainerFeatureEventDispatchPlan.FeatureEntry(id, name, trigger, enabled, kind);
    }

    @Test
    void preservesTrainerInsertionOrderAndCollectsFeaturesEdgesThenKnownFeatures() {
        var first = new TrainerFeatureEventDispatchPlan.TrainerEntry(
                "trainer-b",
                List.of(
                        feature("alpha", "Alpha", " ROUND_START ", null, ""),
                        feature("duplicate", "Duplicate", "round_start", true, "")
                ),
                List.of(
                        feature("duplicate", "Duplicate edge", "round_start", true, ""),
                        feature("edge-hit", "Edge Hit", "round_start", true, "")
                ),
                List.of(feature("known-hit", "Known Hit", "round_start", true, ""))
        );
        var second = new TrainerFeatureEventDispatchPlan.TrainerEntry(
                "trainer-a",
                List.of(
                        feature("disabled", "Disabled", "round_start", false, ""),
                        feature("wrong", "Wrong", "turn_start", true, ""),
                        feature("Named Feature", "", "ROUND_START", true, "")
                ),
                List.of(),
                List.of()
        );

        assertEquals(
                List.of(
                        new TrainerFeatureEventDispatchPlan.Invocation("trainer-b", "alpha", "feature"),
                        new TrainerFeatureEventDispatchPlan.Invocation("trainer-b", "edge-hit", "edge"),
                        new TrainerFeatureEventDispatchPlan.Invocation("trainer-b", "known-hit", "feature"),
                        new TrainerFeatureEventDispatchPlan.Invocation("trainer-a", "named-feature", "feature")
                ),
                TrainerFeatureEventDispatchPlan.resolve(" Round_Start ", List.of(first, second))
        );
    }

    @Test
    void duplicateIdentityUsesNormalizedFeatureIdentifierAcrossSources() {
        var trainer = new TrainerFeatureEventDispatchPlan.TrainerEntry(
                "trainer",
                List.of(feature("", "Power Play", "round_start", true, "")),
                List.of(feature("power-play", "Other", "round_start", true, "")),
                List.of(feature("POWER PLAY", "Known", "round_start", true, ""))
        );
        assertEquals(
                List.of(new TrainerFeatureEventDispatchPlan.Invocation("trainer", "power-play", "feature")),
                TrainerFeatureEventDispatchPlan.resolve("round_start", List.of(trainer))
        );
    }

    @Test
    void blankTriggerOrNoTrainersProduceNoInvocation() {
        var trainer = new TrainerFeatureEventDispatchPlan.TrainerEntry("trainer", List.of(), List.of(), List.of());
        assertEquals(List.of(), TrainerFeatureEventDispatchPlan.resolve("", List.of(trainer)));
        assertEquals(List.of(), TrainerFeatureEventDispatchPlan.resolve("round_start", List.of()));
    }
}
