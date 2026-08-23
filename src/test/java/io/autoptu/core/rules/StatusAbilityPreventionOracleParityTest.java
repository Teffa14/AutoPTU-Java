package io.autoptu.core.rules;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusAbilityPreventionOracleParityTest {
    @Test
    void declarativeAbilityBlockersMatchPinnedPythonContract() throws IOException {
        String property = System.getProperty("autoptu.status.application.oracle");
        Assumptions.assumeTrue(property != null && !property.isBlank());
        Map<String, Integer> oracle = readOracle(Path.of(property));

        assertEquals(1, oracle.get("ability_prevention_respects_suppression"));
        assertEquals(1, oracle.get("suppression_includes_ignore_defensive_abilities"));
        assertEquals(1, oracle.get("own_tempo_blocks_confusion"));
        assertEquals(1, oracle.get("oblivious_blocks_enraged_infatuated"));
        assertEquals(1, oracle.get("run_away_blocks_slowed_stuck_trapped"));
        assertEquals(1, oracle.get("immunity_blocks_poison_family"));
        assertEquals(1, oracle.get("insomnia_blocks_sleep_family"));
        assertEquals(1, oracle.get("vital_spirit_blocks_sleep_family"));

        assertBlocked("Own Tempo", "Confusion", "Own Tempo");
        assertBlocked("Oblivious", "Infatuated", "Oblivious");
        assertBlocked("Run Away", "Stuck", "Run Away");
        assertBlocked("Inner Focus", "Flinched", "Inner Focus");
        assertBlocked("Immunity", "Badly Poisoned", "Immunity");
        assertBlocked("Insomnia", "Sleep", "Insomnia");
        assertBlocked("Vital Spirit", "Asleep", "Vital Spirit");

        assertTrue(StatusAbilityPreventionResolution.blockingAbility(
                List.of("Own Tempo"), "Confused", true).isEmpty());
        assertTrue(StatusAbilityPreventionResolution.blockingAbility(
                List.of("Blaze"), "Confused", false).isEmpty());
    }

    private static void assertBlocked(String ability, String status, String expected) {
        assertEquals(expected, StatusAbilityPreventionResolution.blockingAbility(
                List.of(ability), status, false).orElseThrow());
    }

    private static Map<String, Integer> readOracle(Path path) throws IOException {
        Map<String, Integer> result = new HashMap<>();
        List<String> lines = Files.readAllLines(path);
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            String[] parts = lines.get(i).split("\\t");
            result.put(parts[0], Integer.parseInt(parts[1]));
        }
        return result;
    }
}
