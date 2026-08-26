package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ChroniclerProfileMetadataOracleParityTest {
    @Test
    void matchesPinnedPythonMetadataContract() throws IOException {
        String oracle = System.getenv("AUTOPTU_CHRONICLER_PROFILE_METADATA_ORACLE");
        assumeTrue(oracle != null && !oracle.isBlank(), "Chronicler metadata fixture not configured");

        List<String> lines = Files.readAllLines(Path.of(oracle));
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            expected.put(fields[0], Integer.parseInt(fields[1]));
        }
        expected.forEach((name, value) -> assertEquals(1, value, name));

        ChroniclerProfileMetadata metadata = new ChroniclerProfileMetadata(
                List.of(" Profile ", "PROFILE", "travel"),
                Map.of(
                        "pokemon", List.of("Pikachu", " pikachu ", "Raichu"),
                        "ability", List.of("Static", "STATIC", "Lightning Rod"),
                        "move", List.of("Thunderbolt", "Thunderbolt"),
                        "trainer", List.of("Ace", "Ace")
                )
        );

        assertTrue(metadata.hasArchive("profile"));
        assertTrue(metadata.hasArchive(" PROFILE "));
        assertFalse(metadata.hasArchive("other"));
        assertEquals(List.of("Pikachu", "Raichu"), metadata.records("pokemon"));
        assertEquals(List.of("Static", "Lightning Rod"), metadata.records("ability"));
        assertEquals(List.of("Thunderbolt", "Thunderbolt"), metadata.records("move"));
        assertEquals(List.of("Ace", "Ace"), metadata.records("trainer"));
        assertEquals(List.of(), metadata.records("unknown"));
    }
}
