package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterceptCheckResolutionTest {
    @Test
    void oracleContractMatchesPinnedPython() throws IOException {
        String fixturePath = System.getProperty("autoptu.intercept.check.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(fixturePath));

        for (String key : new String[]{
                "uses_d20",
                "uses_best_acrobatics_athletics",
                "uses_justified_errata",
                "uses_terrain_intercept_bonus",
                "dc_is_distance_times_three",
                "coaching_can_force_success",
                "success_uses_greater_equal",
                "terrain_requires_survivalist",
                "terrain_uses_naturewalk_match",
                "terrain_skill_athletics",
                "terrain_skill_acrobatics",
                "terrain_skill_stealth",
                "terrain_skill_perception",
                "terrain_skill_survival"
        }) {
            assertEquals(1, fixture.get(key), key);
        }
        assertEquals(4, fixture.get("justified_errata_bonus"), "justified_errata_bonus");
        assertEquals(2, fixture.get("terrain_skill_bonus"), "terrain_skill_bonus");
        assertEquals(5, fixture.get("terrain_skill_count"), "terrain_skill_count");
    }

    @Test
    void usesBestSkillAndStacksJustifiedAndTerrain() {
        InterceptCheckResolution.Result result = InterceptCheckResolution.resolve(
                new InterceptCheckResolution.Input(7, 5, 4, 6, 2, 1, false)
        );

        assertEquals(6, result.skillBonus());
        assertEquals(16, result.total());
        assertEquals(15, result.dc());
        assertTrue(result.success());
    }

    @Test
    void equalitySucceedsAndOneBelowFails() {
        InterceptCheckResolution.Result equal = InterceptCheckResolution.resolve(
                new InterceptCheckResolution.Input(9, 4, 3, 1, 0, 0, false)
        );
        InterceptCheckResolution.Result below = InterceptCheckResolution.resolve(
                new InterceptCheckResolution.Input(8, 4, 3, 1, 0, 0, false)
        );

        assertEquals(12, equal.total());
        assertEquals(12, equal.dc());
        assertTrue(equal.success());
        assertFalse(below.success());
    }

    @Test
    void coachingForcesSuccessWithoutChangingArithmetic() {
        InterceptCheckResolution.Result result = InterceptCheckResolution.resolve(
                new InterceptCheckResolution.Input(1, 6, 0, 0, 0, 0, true)
        );

        assertEquals(1, result.total());
        assertEquals(18, result.dc());
        assertTrue(result.success());
        assertTrue(result.coachingAutomaticSuccess());
    }

    private static Map<String, Integer> readFixture(Path path) throws IOException {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            values.put(parts[0], Integer.parseInt(parts[1]));
        }
        return values;
    }
}
