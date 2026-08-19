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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusMetadataOracleParityTest {
    @Test
    void flinchRoundMetadataRequiredByPythonLivesInAuthoritativeBattleState() throws IOException {
        String oraclePath = System.getProperty("autoptu.phase.lifecycle.oracle");
        Assumptions.assumeTrue(oraclePath != null && !oraclePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(oraclePath));
        assertEquals(1, fixture.get("flinch_phase_reads_applied_round_metadata"));

        BattleRuntimeState state = state();
        state.replaceStatusEntries("actor", List.of(new StatusEntry("flinched", Map.of(
                "applied_round", 7,
                "source", "move:fake-out"
        ))));

        StatusEntry entry = state.statusEntry("actor", "Flinched").orElseThrow();
        assertEquals(7, entry.intPayload("applied_round").orElseThrow());
        assertEquals("move:fake-out", entry.stringPayload("source").orElseThrow());
        assertTrue(state.hasStatus("actor", "FLINCHED"));
        assertEquals(Set.of("flinched"), state.statuses("actor"));
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 1),
                20,
                20,
                new ActionBudget()
        );
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor)
        );
    }

    private static Map<String, Integer> readFixture(Path path) throws IOException {
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            values.put(parts[0], Integer.parseInt(parts[1]));
        }
        return values;
    }
}
