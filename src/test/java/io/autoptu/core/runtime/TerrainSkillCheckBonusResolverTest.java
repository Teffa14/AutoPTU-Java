package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainSkillCheckBonusResolverTest {
    @Test
    void grantsTwoForEveryPythonEligibleSkillWithSurvivalistAndMatchingNaturewalk() {
        for (String skill : List.of("Athletics", "Acrobatics", "Stealth", "Perception", "Survival")) {
            assertEquals(
                    2,
                    TerrainSkillCheckBonusResolver.resolve(
                            skill,
                            true,
                            List.of("Forest"),
                            List.of("Dense Forest")
                    ),
                    skill
            );
        }
    }

    @Test
    void requiresBothSurvivalistAndNaturewalkTerrainMatch() {
        assertEquals(0, TerrainSkillCheckBonusResolver.resolve(
                "Athletics", false, List.of("Forest"), List.of("Forest")
        ));
        assertEquals(0, TerrainSkillCheckBonusResolver.resolve(
                "Athletics", true, List.of("Forest"), List.of("Desert")
        ));
        assertEquals(0, TerrainSkillCheckBonusResolver.resolve(
                "Athletics", true, List.of(), List.of("Forest")
        ));
        assertEquals(0, TerrainSkillCheckBonusResolver.resolve(
                "Athletics", true, List.of("Forest"), List.of()
        ));
    }

    @Test
    void rejectsSkillsOutsidePinnedPythonAllowlist() {
        assertEquals(0, TerrainSkillCheckBonusResolver.resolve(
                "Guile", true, List.of("Urban"), List.of("Urban")
        ));
        assertEquals(0, TerrainSkillCheckBonusResolver.resolve(
                "Focus", true, List.of("Forest"), List.of("Forest")
        ));
    }

    @Test
    void matchesPythonTokenLandStemAndAliasContextSemantics() {
        assertTrue(TerrainSkillCheckBonusResolver.matchesNaturewalkTerrain(
                List.of("Mountain/Cave"), List.of("Rocky Cave")
        ));
        assertTrue(TerrainSkillCheckBonusResolver.matchesNaturewalkTerrain(
                List.of("Grassland"), List.of("Grass")
        ));
        assertTrue(TerrainSkillCheckBonusResolver.matchesNaturewalkTerrain(
                List.of("Forest"), List.of("Desert", "Forest")
        ));
        assertFalse(TerrainSkillCheckBonusResolver.matchesNaturewalkTerrain(
                List.of("Ocean"), List.of("Urban", "Forest")
        ));
    }
}
