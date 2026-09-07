package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainerFeatureEventDispatchPlanTest {
    @Test
    void mirrorsPythonTrainerOrderingFeatureOrderingAndEventFiltering() {
        List<TrainerFeatureEventDispatchPlan.TrainerEntry> trainers = List.of(
                new TrainerFeatureEventDispatchPlan.TrainerEntry(
                        "z-trainer", "B", 0, "Zulu",
                        List.of("Always Hook", "Wrong Event", "")
                ),
                new TrainerFeatureEventDispatchPlan.TrainerEntry(
                        "a-two", "A", 2, "Alpha",
                        List.of("Multi Event", "Unknown Hook")
                ),
                new TrainerFeatureEventDispatchPlan.TrainerEntry(
                        "a-one-z", "A", 1, "Zulu",
                        List.of(" Round Hook ", "Always Hook")
                ),
                new TrainerFeatureEventDispatchPlan.TrainerEntry(
                        "a-one-a", "A", 1, "Alpha",
                        List.of("Always Hook", "Multi Event")
                )
        );

        List<TrainerFeatureEventDispatchPlan.Invocation> actual = TrainerFeatureEventDispatchPlan.resolve(
                "round_start",
                trainers,
                Set.of("round hook", "always hook", "wrong event", "multi event"),
                Map.of(
                        "round hook", new TrainerFeatureEventDispatchPlan.HookMetadata("round_start", Set.of()),
                        "wrong event", new TrainerFeatureEventDispatchPlan.HookMetadata("turn_start", Set.of()),
                        "multi event", new TrainerFeatureEventDispatchPlan.HookMetadata("", Set.of("round_start", "battle_start"))
                )
        );

        assertEquals(
                List.of(
                        new TrainerFeatureEventDispatchPlan.Invocation("a-one-a", "Always Hook"),
                        new TrainerFeatureEventDispatchPlan.Invocation("a-one-a", "Multi Event"),
                        new TrainerFeatureEventDispatchPlan.Invocation("a-one-z", "Round Hook"),
                        new TrainerFeatureEventDispatchPlan.Invocation("a-one-z", "Always Hook"),
                        new TrainerFeatureEventDispatchPlan.Invocation("a-two", "Multi Event"),
                        new TrainerFeatureEventDispatchPlan.Invocation("z-trainer", "Always Hook")
                ),
                actual
        );
    }

    @Test
    void blankTriggerAndUnregisteredFeaturesProduceNoInvocation() {
        var trainer = new TrainerFeatureEventDispatchPlan.TrainerEntry(
                "trainer", "", 0, "Trainer", List.of("Feature")
        );
        assertEquals(List.of(), TrainerFeatureEventDispatchPlan.resolve("", List.of(trainer), Set.of("feature"), Map.of()));
        assertEquals(List.of(), TrainerFeatureEventDispatchPlan.resolve("round_start", List.of(trainer), Set.of(), Map.of()));
    }
}
