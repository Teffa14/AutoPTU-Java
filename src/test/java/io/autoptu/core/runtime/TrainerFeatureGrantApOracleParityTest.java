package io.autoptu.core.runtime;

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

class TrainerFeatureGrantApOracleParityTest {
    @Test
    void grantApMatchesPinnedPythonDispatcher() throws IOException {
        Path fixture = Path.of("build/oracle/trainer-feature-grant-ap.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, Expected> expected = readFixture(fixture);

        LinkedHashMap<String, Map<String, ?>> cases = new LinkedHashMap<>();
        cases.put("default_amount_self", Map.of("type", "grant_ap"));
        cases.put("explicit_amount_self", Map.of("type", "grant_ap", "amount", 3));
        cases.put("zero_not_applied", Map.of("type", "grant_ap", "amount", 0));
        cases.put("negative_not_applied", Map.of("type", "grant_ap", "amount", -2));
        cases.put("float_string_int_like", Map.of("type", "grant_ap", "amount", "4.9"));
        cases.put("ally_alias_is_self", Map.of("type", "grant_ap", "amount", 2, "trainer_scope", "ally"));
        cases.put("enemy_targets_other_trainers", Map.of("type", "grant_ap", "amount", 2, "trainer_scope", "enemy"));
        cases.put("all_targets_in_order", Map.of("type", "grant_ap", "amount", 1, "trainer_scope", "all"));
        cases.put("explicit_trainer_id", Map.of("type", "grant_ap", "amount", 5, "trainer", "t2"));
        cases.put("unknown_selector_falls_back_self", Map.of("type", "grant_ap", "amount", 2, "trainer", "missing"));
        cases.put("trainer_scope_precedes_trainer", Map.of("type", "grant_ap", "amount", 2, "trainer_scope", "enemy", "trainer", "t1"));
        cases.put("false_scope_falls_back_trainer", Map.of("type", "grant_ap", "amount", 2, "trainer_scope", false, "trainer", "t2"));

        assertEquals(expected.keySet(), cases.keySet());
        TrainerFeatureEffectRegistry registry = new TrainerFeatureEffectRegistry();
        for (Map.Entry<String, Map<String, ?>> entry : cases.entrySet()) {
            BattleRuntimeState state = state();
            TrainerFeatureEffectRegistry.EffectResult result = registry.apply(
                    new TrainerFeatureEffectRegistry.EffectContext(state, "t1", "", Map.of(), Map.of()),
                    entry.getValue()
            );
            Expected exp = expected.get(entry.getKey());
            assertEquals(exp.applied(), result.applied(), entry.getKey() + " applied");
            assertEquals(exp.effectType(), result.effectType(), entry.getKey() + " type");
            assertEquals(exp.targets(), String.join(",", result.targets()), entry.getKey() + " Pokemon targets");
            assertEquals(exp.amount(), result.details().containsKey("amount") ? String.valueOf(result.details().get("amount")) : "", entry.getKey() + " amount");
            assertEquals(exp.changedTrainers(), changedTrainers(result), entry.getKey() + " changed Trainers");
            assertEquals(exp.apSnapshot(), apSnapshot(state), entry.getKey() + " AP state");
        }
    }

    private static BattleRuntimeState state() {
        BattleRuntimeState state = new BattleRuntimeState(new MovementGrid(2, 2, Set.of(), Map.of()), List.of());
        state.putTrainer(new TrainerRuntimeState("t1", List.of(), 5));
        state.putTrainer(new TrainerRuntimeState("t2", List.of(), 2));
        state.putTrainer(new TrainerRuntimeState("t3", List.of(), 0));
        return state;
    }

    private static String changedTrainers(TrainerFeatureEffectRegistry.EffectResult result) {
        Object raw = result.details().get("trainers");
        if (!(raw instanceof List<?> trainers)) return "";
        return trainers.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private static String apSnapshot(BattleRuntimeState state) {
        return String.join(",",
                "t1=" + state.requireTrainer("t1").ap(),
                "t2=" + state.requireTrainer("t2").ap(),
                "t3=" + state.requireTrainer("t3").ap());
    }

    private static Map<String, Expected> readFixture(Path path) throws IOException {
        LinkedHashMap<String, Expected> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            out.put(parts[0], new Expected("1".equals(parts[1]), parts[2], parts[3], parts[4], parts[5], parts[6]));
        }
        return out;
    }

    private record Expected(
            boolean applied,
            String effectType,
            String targets,
            String amount,
            String changedTrainers,
            String apSnapshot
    ) {}
}
