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
                List.of(" Profile Album ", "PROFILE", "travel album", "unknown"),
                Map.of(
                        "profile", List.of("Pikachu", " pikachu ", "Mr.   Mime"),
                        "technique", List.of("Thunderbolt", "THUNDERBOLT", "Volt   Tackle"),
                        "travel", List.of("Viridian Forest", "viridian forest", "Route   1")
                ),
                " KeenEye "
        );

        assertTrue(metadata.hasArchive("profile"));
        assertTrue(metadata.hasArchive(" PROFILE ALBUM "));
        assertTrue(metadata.hasArchive("travel"));
        assertFalse(metadata.hasArchive("other"));
        assertEquals(List.of("Pikachu", "Mr. Mime"), metadata.records("profile"));
        assertEquals(List.of("Thunderbolt", "Volt Tackle"), metadata.records("technique"));
        assertEquals(List.of("Viridian Forest", "Route 1"), metadata.records("travel"));
        assertEquals(List.of(), metadata.records("unknown"));
        assertEquals("Keen Eye", metadata.travelAbility());
        assertEquals("Perception", new ChroniclerProfileMetadata(List.of(), Map.of(), "perception").travelAbility());
        assertEquals("", new ChroniclerProfileMetadata(List.of(), Map.of(), "unknown").travelAbility());
    }
}
