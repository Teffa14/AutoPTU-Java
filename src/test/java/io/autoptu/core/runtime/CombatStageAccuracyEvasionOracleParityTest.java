package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CombatStageAccuracyEvasionOracleParityTest {
    @Test
    void oracleKeepsAccuracyAndEvasionOnGenericCombatStagePath() throws IOException {
        String oracle = System.getenv("AUTOPTU_COMBAT_STAGE_ACCURACY_EVASION_ORACLE");
        assumeTrue(oracle != null && !oracle.isBlank(), "Accuracy/Evasion Combat Stage fixture not configured");

        Map<String, String> values = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(Path.of(oracle));
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            values.put(fields[0], fields[1]);
        }

        for (String key : List.of(
                "stat_parameter",
                "dynamic_stage_read",
                "dynamic_stage_write",
                "clamps_lower_minus_six",
                "clamps_upper_plus_six",
                "forwards_stat_to_hook_context",
                "no_literal_stat_allowlist",
                "parser_mentions_accuracy",
                "parser_mentions_evasion")) {
            assertEquals("1", values.get(key), key);
        }
    }
}
