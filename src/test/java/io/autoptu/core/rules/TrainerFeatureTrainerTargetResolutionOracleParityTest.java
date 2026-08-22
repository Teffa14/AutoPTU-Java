package io.autoptu.core.rules;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrainerFeatureTrainerTargetResolutionOracleParityTest {
    @Test
    void trainerTargetScopesMatchPinnedPythonDispatcher() throws IOException {
        Path fixture = Path.of("build/oracle/trainer-feature-trainer-targets.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = readFixture(fixture);

        LinkedHashMap<String, Map<String, ?>> cases = new LinkedHashMap<>();
        cases.put("default_self", Map.of());
        cases.put("self", Map.of("trainer_scope", "self"));
        cases.put("ally_alias", Map.of("trainer_scope", "ally"));
        cases.put("allies_alias", Map.of("trainer_scope", "allies"));
        cases.put("self_team_alias", Map.of("trainer_scope", "self_team"));
        cases.put("own_alias", Map.of("trainer_scope", "own"));
        cases.put("enemy", Map.of("trainer_scope", "enemy"));
        cases.put("foe_alias", Map.of("trainer_scope", "foe"));
        cases.put("opponent_alias", Map.of("trainer_scope", "opponent"));
        cases.put("all", Map.of("trainer_scope", "all"));
        cases.put("any_alias", Map.of("trainer_scope", "any"));
        cases.put("explicit_other_trainer", Map.of("trainer_scope", "t3"));
        cases.put("explicit_source_trainer", Map.of("trainer_scope", "t1"));
        cases.put("unknown_falls_back_self", Map.of("trainer_scope", "missing"));
        cases.put("trainer_field_fallback", Map.of("trainer", "t2"));
        cases.put("trainer_scope_precedes_trainer", Map.of("trainer_scope", "t3", "trainer", "t2"));
        cases.put("blank_scope_uses_trainer", Map.of("trainer_scope", "", "trainer", "t2"));
        cases.put("false_scope_uses_trainer", Map.of("trainer_scope", false, "trainer", "t2"));
        cases.put("zero_scope_uses_trainer", Map.of("trainer_scope", 0, "trainer", "t2"));
        cases.put("blank_both_defaults_self", Map.of("trainer_scope", "", "trainer", ""));
        cases.put("normalizes_case_and_space", Map.of("trainer_scope", "  T3  "));

        assertEquals(expected.keySet(), cases.keySet());
        List<String> trainers = List.of("t1", "t2", "t3");
        for (Map.Entry<String, Map<String, ?>> entry : cases.entrySet()) {
            List<String> actual = TrainerFeatureTrainerTargetResolution.resolve("t1", trainers, entry.getValue());
            assertEquals(expected.get(entry.getKey()), String.join(",", actual), entry.getKey());
        }
    }

    @Test
    void unknownSourceTrainerFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> TrainerFeatureTrainerTargetResolution.resolve("missing", List.of("t1"), Map.of()));
    }

    private static Map<String, String> readFixture(Path path) throws IOException {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            out.put(parts[0], parts.length > 1 ? parts[1] : "");
        }
        return out;
    }
}
