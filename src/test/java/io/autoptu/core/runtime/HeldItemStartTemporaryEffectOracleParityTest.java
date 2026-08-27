package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeldItemStartTemporaryEffectOracleParityTest {
    @Test
    void genericHeldItemStartContractMatchesPinnedPython() throws IOException {
        String fixturePath = System.getProperty("autoptu.held.item.start.temp.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(fixturePath));

        assertEquals(1, fixture.get("base_stat_changes_before_scalars"));
        assertEquals(1, fixture.get("scalars_before_accuracy"));
        assertEquals(1, fixture.get("accuracy_before_evasion"));
        assertEquals(1, fixture.get("stat_modifier_duplicate_key_stat_source"));
        assertEquals(1, fixture.get("stat_scalar_duplicate_key_stat_source"));
        assertEquals(1, fixture.get("accuracy_carries_null_type"));
        assertEquals(1, fixture.get("status_evasion_scope"));
        assertEquals(1, fixture.get("all_evasion_scope"));
        assertEquals(1, fixture.get("source_is_display_name"));
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
