package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TileEntryTrapOracleContractTest {
    @Test
    void matchesPinnedPythonEntryContract() throws Exception {
        String fixture = System.getenv("AUTOPTU_TILE_ENTRY_TRAP_ORACLE");
        if (fixture == null || fixture.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixture));
        assertEquals("contract\tvalue", lines.getFirst());
        Map<String, String> contract = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\\t", -1);
            assertEquals(2, fields.length);
            contract.put(fields[0], fields[1]);
        }

        assertEquals("skip", contract.get("zero_layers"));
        assertEquals("skip", contract.get("same_team_source"));
        assertEquals("has_special_branch", contract.get("naturewalk_match"));
        assertEquals("trigger", contract.get("enemy_entry"));
        assertEquals("whole_trap_key", contract.get("trigger_consumption"));
        assertEquals("source_id,terrains,coord,target_hp", contract.get("trigger_provenance"));
    }
}
