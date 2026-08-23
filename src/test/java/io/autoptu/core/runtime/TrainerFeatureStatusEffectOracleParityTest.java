package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainerFeatureStatusEffectOracleParityTest {
    @Test
    void trainerFeatureStatusEffectsMatchPinnedPythonDispatcher() throws IOException {
        Path fixture = Path.of("build/oracle/status-stack.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, Expected> expected = readFixture(fixture);
        LinkedHashMap<String, EffectCase> cases = cases();
        assertEquals(expected.keySet(), cases.keySet());

        TrainerFeatureEffectRegistry registry = new TrainerFeatureEffectRegistry();
        for (Map.Entry<String, EffectCase> entry : cases.entrySet()) {
            BattleRuntimeState state = state();
            state.replaceStatusEntries("ally", entry.getValue().initialStatuses());
            TrainerFeatureEffectRegistry.EffectResult result = registry.apply(
                    new TrainerFeatureEffectRegistry.EffectContext(
                            state,
                            "t1",
                            "ally",
                            Map.of("name", "Stack Test"),
                            Map.of()
                    ),
                    entry.getValue().effect()
            );
            Expected exp = expected.get(entry.getKey());
            assertEquals(exp.applied(), result.applied(), entry.getKey() + " applied");
            assertEquals(exp.effectType(), result.effectType(), entry.getKey() + " effect type");
            assertEquals(exp.targets(), String.join(",", result.targets()), entry.getKey() + " targets");
            assertEquals(exp.snapshot(), snapshot(state), entry.getKey() + " status snapshot");
            assertEquals(exp.detailStatus(), stringDetail(result, "status"), entry.getKey() + " detail status");
            assertEquals(exp.detailDuration(), stringDetail(result, "duration"), entry.getKey() + " detail duration");
            assertEquals(exp.removed(), listDetail(result, "removed"), entry.getKey() + " removed");
        }
    }

    private static LinkedHashMap<String, EffectCase> cases() {
        LinkedHashMap<String, EffectCase> cases = new LinkedHashMap<>();
        cases.put("apply_new_duration", new EffectCase(
                List.of(),
                Map.of("type", "apply_status", "status", "Poisoned", "duration", 3, "target_rules", Map.of("scope", "actor"))
        ));
        cases.put("refresh_first_shorter_duration", new EffectCase(
                List.of(
                        new StatusEntry("Poisoned", Map.of("source", "move:a", "remaining", 2, "duration", 2)),
                        new StatusEntry("Poisoned", Map.of("source", "move:b", "remaining", 1, "duration", 1))
                ),
                Map.of("type", "apply_status", "status", "Poisoned", "duration", 5, "target_rules", Map.of("scope", "actor"))
        ));
        cases.put("existing_longer_no_change", new EffectCase(
                List.of(new StatusEntry("Poisoned", Map.of("source", "move:a", "remaining", 7, "duration", 7))),
                Map.of("type", "apply_status", "status", "Poisoned", "duration", 5, "target_rules", Map.of("scope", "actor"))
        ));
        cases.put("stack_appends_duplicate", new EffectCase(
                List.of(
                        new StatusEntry("Poisoned", Map.of("source", "move:a", "remaining", 2, "duration", 2)),
                        new StatusEntry("Burned")
                ),
                Map.of("type", "apply_status", "status", "Poisoned", "duration", 4, "stack", true, "target_rules", Map.of("scope", "actor"))
        ));
        cases.put("zero_duration_existing_no_change", new EffectCase(
                List.of(new StatusEntry("Poisoned", Map.of("source", "move:a", "remaining", 2, "duration", 2))),
                Map.of("type", "apply_status", "status", "Poisoned", "target_rules", Map.of("scope", "actor"))
        ));
        cases.put("remove_named_removes_all_duplicates", new EffectCase(
                List.of(
                        new StatusEntry("Poisoned", Map.of("source", "move:a")),
                        new StatusEntry("Burned"),
                        new StatusEntry("POISONED", Map.of("source", "move:b"))
                ),
                Map.of("type", "remove_status", "status", "Poisoned", "target_rules", Map.of("scope", "actor"))
        ));
        cases.put("remove_all_clears_every_entry", new EffectCase(
                List.of(
                        new StatusEntry("Poisoned", Map.of("source", "move:a")),
                        new StatusEntry("Burned"),
                        new StatusEntry("Poisoned", Map.of("source", "move:b"))
                ),
                Map.of("type", "remove_status", "all", true, "target_rules", Map.of("scope", "actor"))
        ));
        return cases;
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState ally = new RuntimeCombatantState(
                "ally",
                MovementProfile.walking(new GridCoord(0, 0), 3),
                20,
                20,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(3, 3, Set.of(), Map.of()),
                List.of(ally)
        );
        state.putTrainer(new TrainerRuntimeState("t1", List.of(), 0));
        state.bindController("ally", "t1");
        return state;
    }

    private static String snapshot(BattleRuntimeState state) {
        return state.statusEntries("ally").stream()
                .map(TrainerFeatureStatusEffectOracleParityTest::serialize)
                .collect(Collectors.joining(";"));
    }

    private static String serialize(StatusEntry entry) {
        return String.join("|",
                entry.name(),
                entry.stringPayload("source").orElse(""),
                entry.intPayload("remaining").map(String::valueOf).orElse(""),
                entry.intPayload("duration").map(String::valueOf).orElse(""));
    }

    private static String stringDetail(TrainerFeatureEffectRegistry.EffectResult result, String key) {
        Object value = result.details().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String listDetail(TrainerFeatureEffectRegistry.EffectResult result, String key) {
        Object value = result.details().get(key);
        if (!(value instanceof List<?> list)) return "";
        ArrayList<String> values = new ArrayList<>();
        for (Object item : list) values.add(String.valueOf(item));
        return String.join(",", values);
    }

    private static Map<String, Expected> readFixture(Path path) throws IOException {
        LinkedHashMap<String, Expected> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            out.put(parts[0], new Expected(
                    "1".equals(parts[1]), parts[2], parts[3], parts[4], parts[5], parts[6], parts[7]
            ));
        }
        return out;
    }

    private record EffectCase(List<StatusEntry> initialStatuses, Map<String, ?> effect) {}
    private record Expected(
            boolean applied,
            String effectType,
            String targets,
            String snapshot,
            String detailStatus,
            String detailDuration,
            String removed
    ) {}
}