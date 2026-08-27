package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeldItemRuleCatalogOracleParityTest {
    @Test
    void catalogBoundaryMatchesPinnedPython() throws IOException {
        String fixturePath = System.getProperty("autoptu.held.item.catalog.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(fixturePath));

        assertEquals(1, fixture.get("lookup_normalizes_strip_lower"));
        assertEquals(1, fixture.get("exact_lookup_precedes_compact_fallback"));
        assertEquals(1, fixture.get("compact_removes_non_alphanumeric"));
        assertEquals(1, fixture.get("compact_fallback_scans_catalog"));
        assertEquals(1, fixture.get("start_uses_catalog_entry_parser"));
        assertEquals(1, fixture.get("start_uses_display_item_name_as_source"));
        assertEquals(1, fixture.get("start_uses_entry_normalized_name_for_rules"));
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
