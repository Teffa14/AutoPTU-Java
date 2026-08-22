package io.autoptu.core.rules;

import io.autoptu.core.random.PythonRandom;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainerFeatureContextResolutionOracleParityTest {
    private static final long DEFAULT_SEED = 2026L;

    @Test
    void contextGatesAndRngConsumptionMatchPinnedPythonDispatcher() throws IOException {
        Path fixturePath = Path.of("build/oracle/trainer-feature-context.tsv");
        Assumptions.assumeTrue(Files.exists(fixturePath));
        Map<String, Expected> expected = readFixture(fixturePath);

        LinkedHashMap<String, Case> cases = new LinkedHashMap<>();
        cases.put("baseline", testCase(Map.of()));
        cases.put("non_dict_conditions", rawConditions("anything"));
        cases.put("actor_required_missing", testCase(Map.of("actor_required", true)).actor(null, null, false, false));
        cases.put("self_scope_pass", testCase(Map.of("actor_scope", "self_team")));
        cases.put("self_scope_fail", testCase(Map.of("actor_scope", "ally")).actor("mon-b", "trainer-b", true, true));
        cases.put("enemy_scope_pass", testCase(Map.of("actor_scope", "enemy")).actor("mon-b", "trainer-b", true, true));
        cases.put("enemy_scope_missing_fail", testCase(Map.of("actor_scope", "foe")).actor("missing", null, false, false));
        cases.put("trainer_scope_pass", testCase(Map.of("actor_scope", "trainer")).actor("trainer-a", "trainer-a", false, false));
        cases.put("trainer_scope_fail", testCase(Map.of("actor_scope", "trainer")));
        cases.put("pokemon_scope_pass", testCase(Map.of("actor_scope", "pokemon")));
        cases.put("pokemon_scope_fail", testCase(Map.of("actor_scope", "pokemon")).actor("trainer-a", "trainer-a", false, false));
        cases.put("phase_payload_pass", testCase(Map.of("phase_in", List.of("start", "action"))).payload(Map.of("phase", " ACTION ")));
        cases.put("phase_battle_fallback_pass", testCase(Map.of("phase", "action")));
        cases.put("phase_fail", testCase(Map.of("phase", "end")));
        cases.put("action_type_pass", testCase(Map.of("action_types", List.of("shift", "standard"))).payload(Map.of("action_type", "STANDARD")));
        cases.put("action_type_fail", testCase(Map.of("action_type", "free")).payload(Map.of("action_type", "standard")));
        cases.put("move_name_pass", testCase(Map.of("move_names", List.of("tackle", "ember"))).payload(Map.of("move_name", "EMBER")));
        cases.put("move_category_pass", testCase(Map.of("move_category", "physical")).payload(Map.of("move_category", "PHYSICAL")));
        cases.put("actor_active_pass", testCase(Map.of("actor_active", "yes")));
        cases.put("actor_active_fail", testCase(Map.of("actor_active", true)).actor("mon-a-inactive", "trainer-a", true, false));
        cases.put("actor_active_missing_fail", testCase(Map.of("actor_active", false)).actor("trainer-a", "trainer-a", false, false));
        cases.put("min_round_pass", testCase(Map.of("min_round", 3)));
        cases.put("min_round_fail", testCase(Map.of("min_round", 4)));
        cases.put("max_round_pass", testCase(Map.of("max_round", 3)));
        cases.put("max_round_fail", testCase(Map.of("max_round", 2)));
        cases.put("damage_fallback_pass", testCase(Map.of("min_damage", 6)).payload(Map.of("damage", "bad", "damage_dealt", 7)));
        cases.put("damage_direct_precedence_fail", testCase(Map.of("min_damage", 6)).payload(Map.of("damage", 2, "damage_dealt", 99)));
        cases.put("max_damage_fail", testCase(Map.of("max_damage", 5)).payload(Map.of("total_damage", 6)));
        cases.put("once_actor_unused_pass", testCase(Map.of("once_per_actor_per_round", true)));
        cases.put("once_actor_used_fail", testCase(Map.of("once_per_actor_per_round", true)).usage(Map.of("actor_round_mon-a_3", 1)));
        cases.put("chance_zero_no_rng", testCase(Map.of("chance", 0)));
        cases.put("chance_percent", testCase(Map.of("chance", 25)).seed(7));
        cases.put("chance_fraction", testCase(Map.of("chance", 0.75)).seed(7));
        cases.put("chance_clamped_one", testCase(Map.of("chance", 250)).seed(7));
        cases.put("guard_before_chance_no_rng", testCase(Map.of("min_round", 99, "chance", 1.0)).seed(7));

        assertEquals(expected.keySet(), cases.keySet());
        for (Map.Entry<String, Case> entry : cases.entrySet()) {
            Case current = entry.getValue();
            PythonRandom rng = new PythonRandom(current.seed);
            TrainerFeatureContextResolution.Context context = new TrainerFeatureContextResolution.Context(
                    "trainer-a",
                    current.actorId,
                    current.actorTrainerId,
                    current.actorIsPokemon,
                    current.actorActive,
                    current.round,
                    current.phase,
                    current.payload,
                    current.usage,
                    rng
            );
            boolean actual = TrainerFeatureContextResolution.matches(current.feature, context);
            double nextRoll = rng.random();
            Expected oracle = expected.get(entry.getKey());
            assertEquals(oracle.result, actual ? 1 : 0, entry.getKey());
            assertEquals(
                    Double.doubleToLongBits(Double.parseDouble(oracle.nextRoll)),
                    Double.doubleToLongBits(nextRoll),
                    entry.getKey() + " RNG position"
            );
        }
    }

    private static Case testCase(Map<String, ?> conditions) {
        return new Case(
                Map.of("conditions", conditions),
                "mon-a", "trainer-a", true, true,
                3, "ACTION", Map.of(), Map.of(), DEFAULT_SEED
        );
    }

    private static Case rawConditions(Object conditions) {
        return new Case(
                Map.of("conditions", conditions),
                "mon-a", "trainer-a", true, true,
                3, "ACTION", Map.of(), Map.of(), DEFAULT_SEED
        );
    }

    private static Map<String, Expected> readFixture(Path path) throws IOException {
        LinkedHashMap<String, Expected> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t");
            out.put(parts[0], new Expected(Integer.parseInt(parts[1]), parts[2]));
        }
        return out;
    }

    private record Expected(int result, String nextRoll) {}

    private record Case(
            Map<String, ?> feature,
            String actorId,
            String actorTrainerId,
            boolean actorIsPokemon,
            boolean actorActive,
            int round,
            String phase,
            Map<String, ?> payload,
            Map<String, Integer> usage,
            long seed
    ) {
        Case actor(String actorId, String actorTrainerId, boolean actorIsPokemon, boolean actorActive) {
            return new Case(feature, actorId, actorTrainerId, actorIsPokemon, actorActive, round, phase, payload, usage, seed);
        }

        Case payload(Map<String, ?> payload) {
            return new Case(feature, actorId, actorTrainerId, actorIsPokemon, actorActive, round, phase, payload, usage, seed);
        }

        Case usage(Map<String, Integer> usage) {
            return new Case(feature, actorId, actorTrainerId, actorIsPokemon, actorActive, round, phase, payload, usage, seed);
        }

        Case seed(long seed) {
            return new Case(feature, actorId, actorTrainerId, actorIsPokemon, actorActive, round, phase, payload, usage, seed);
        }
    }
}
