package io.autoptu.core.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MoveCombatProfileOracleParityTest {
    @Test
    void matchesPinnedPythonMoveSpecMetadata() throws IOException {
        String oraclePath = System.getProperty("autoptu.move.combat.profile.oracle", "");
        assumeTrue(!oraclePath.isBlank(), "move-combat-profile oracle path not configured");
        Map<String, String> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(oraclePath))) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            expected.put(parts[0], parts.length == 2 ? parts[1] : "");
        }
        assertEquals(expected, generatedFixtures());
    }

    private static Map<String, String> generatedFixtures() {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("defaults", stable(new MoveCombatProfile(2, 8, 20, "Special")));
        rows.put("physical_custom", stable(new MoveCombatProfile(5, 10, 18, "Physical")));
        rows.put("always_hit_special", stable(new MoveCombatProfile(null, 6, 20, "Special")));
        return rows;
    }

    private static String stable(MoveCombatProfile profile) {
        String ac = profile.ac() == null ? "null" : profile.ac().toString();
        return "ac=" + ac
                + "|db=" + profile.damageBase()
                + "|crit=" + profile.critRange()
                + "|category=" + profile.damageCategory();
    }
}
