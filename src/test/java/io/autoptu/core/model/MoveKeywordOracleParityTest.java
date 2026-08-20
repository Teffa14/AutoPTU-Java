package io.autoptu.core.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MoveKeywordOracleParityTest {
    @Test
    void moveKeywordLookupMatchesPinnedPythonOracle() throws IOException {
        String oraclePath = System.getProperty("autoptu.move.keyword.oracle");
        if (oraclePath == null || oraclePath.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertFalse(lines.isEmpty());
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            String[] c = lines.get(index).split("\\t", -1);
            String name = c[0];
            List<String> keywords = c[1].isEmpty()
                    ? List.of()
                    : Arrays.asList(c[1].split("\\|", -1));
            String query = c[2];
            boolean expected = "1".equals(c[3]);

            MoveSpec move = new MoveSpec(
                    "Ranged", "Ranged", 6, 6, null, null, "Range 6", keywords
            );
            assertEquals(expected, move.hasKeyword(query), name);
        }
    }

    @Test
    void keywordSnapshotIsDefensiveAndLegacyConstructorDefaultsEmpty() {
        java.util.ArrayList<String> source = new java.util.ArrayList<>(List.of("Aura"));
        MoveSpec move = new MoveSpec("Ranged", "Ranged", 6, 6, null, null, "Range 6", source);
        source.set(0, "Push");
        assertEquals(true, move.hasKeyword("aura"));
        assertEquals(false, move.hasKeyword("push"));

        MoveSpec legacy = new MoveSpec("Ranged", "Ranged", 6, 6, null, null, "Range 6");
        assertEquals(List.of(), legacy.keywords());
        assertEquals(false, legacy.hasKeyword("aura"));
    }
}
