package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ChroniclerProfileMatchOracleParityTest {
    @Test
    void matchesPinnedPythonContractAndCases() throws IOException {
        String oracle = System.getenv("AUTOPTU_CHRONICLER_PROFILE_MATCH_ORACLE");
        assumeTrue(oracle != null && !oracle.isBlank(), "Chronicler profile-match fixture not configured");

        List<String> lines = Files.readAllLines(Path.of(oracle));
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            expected.put(fields[0], Integer.parseInt(fields[1]));
        }
        expected.forEach((name, value) -> assertEquals(1, value, name));

        ChroniclerProfileMetadata profile = new ChroniclerProfileMetadata(
                List.of("profile"),
                Map.of("profile", List.of("Pikachu", "Raichu", " Brock "))
        );
        ChroniclerProfileMetadata noProfileArchive = new ChroniclerProfileMetadata(
                List.of("technique"),
                Map.of("profile", List.of("Pikachu"))
        );
        ChroniclerProfileMetadata noRecords = new ChroniclerProfileMetadata(List.of("profile"), Map.of());

        assertFalse(ChroniclerProfileMatchResolution.matches(profile, null,
                new ChroniclerProfileMatchResolution.TargetProfile("Pikachu", "Pikachu", "Brock")));
        assertFalse(ChroniclerProfileMatchResolution.matches(noProfileArchive, "target",
                new ChroniclerProfileMatchResolution.TargetProfile("Pikachu", "Pikachu", "Brock")));
        assertFalse(ChroniclerProfileMatchResolution.matches(noRecords, "target",
                new ChroniclerProfileMatchResolution.TargetProfile("Pikachu", "Pikachu", "Brock")));
        assertFalse(ChroniclerProfileMatchResolution.matches(profile, "missing", null));

        assertTrue(ChroniclerProfileMatchResolution.matches(profile, "target",
                new ChroniclerProfileMatchResolution.TargetProfile("  PIKACHU  ", "Other", "Misty")));
        assertTrue(ChroniclerProfileMatchResolution.matches(profile, "target",
                new ChroniclerProfileMatchResolution.TargetProfile("Sparky", "RAICHU", "Misty")));
        assertTrue(ChroniclerProfileMatchResolution.matches(profile, "target",
                new ChroniclerProfileMatchResolution.TargetProfile("Onix", "Onix", "BROCK")));
        assertFalse(ChroniclerProfileMatchResolution.matches(profile, "target",
                new ChroniclerProfileMatchResolution.TargetProfile("Onix", "Onix", null)));
        assertFalse(ChroniclerProfileMatchResolution.matches(ChroniclerProfileMetadata.empty(), "target",
                new ChroniclerProfileMatchResolution.TargetProfile("Pikachu", "Pikachu", "Brock")));
    }
}
