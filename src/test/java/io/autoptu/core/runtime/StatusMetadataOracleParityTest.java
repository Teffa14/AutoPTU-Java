package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusMetadataOracleParityTest {
    @Test
    void flinchRoundMetadataRequiredByPythonCanBeRepresentedCanonically() throws IOException {
        String oraclePath = System.getProperty("autoptu.phase.lifecycle.oracle");
        Assumptions.assumeTrue(oraclePath != null && !oraclePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(oraclePath));
        assertEquals(1, fixture.get("flinch_phase_reads_applied_round_metadata"));

        StatusStateStore store = new StatusStateStore();
        store.put("actor", new StatusEntry("flinched", Map.of(
                "applied_round", 7,
                "source", "move:fake-out"
        )));

        StatusEntry entry = store.find("actor", "Flinched").orElseThrow();
        assertEquals(7, entry.intPayload("applied_round").orElseThrow());
        assertEquals("move:fake-out", entry.stringPayload("source").orElseThrow());
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
