package io.autoptu.core.rules;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.StatFlag;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EvasionResolutionOracleParityTest {
    @Test
    void matchesPinnedPythonEvasionValue() throws IOException {
        String oraclePath = System.getProperty("autoptu.evasion.oracle", "");
        assumeTrue(!oraclePath.isBlank(), "evasion oracle path not configured");
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
        rows.put("physical_base", value(profile(17, 10, 10, 0, Set.of()), 2, 0, 0, false, false, "Physical"));
        rows.put("special_base", value(profile(10, 24, 10, 0, Set.of()), 0, -1, 0, false, false, "Special"));
        rows.put("status_stage", value(profile(10, 10, 19, 2, Set.of()), 0, 0, 1, false, false, "Status"));
        rows.put("status_paralyzed", value(profile(10, 10, 19, 2, Set.of(StatFlag.PARALYZED)), 0, 0, 1, false, false, "Status"));
        rows.put("sleep_suppresses_positive", value(profile(17, 10, 10, 0, Set.of()), 3, 0, 0, true, false, "Physical"));
        rows.put("sleep_keeps_negative", value(profile(17, 10, 10, 0, Set.of()), -2, 0, 0, true, false, "Physical"));
        rows.put("freeze_suppresses_positive", value(profile(17, 10, 10, 0, Set.of()), 4, 0, 0, true, false, "Physical"));
        rows.put("freeze_keeps_negative", value(profile(17, 10, 10, 0, Set.of()), -1, 0, 0, true, false, "Physical"));
        rows.put("heavy_metal_physical", value(profile(19, 10, 10, 0, Set.of(StatFlag.HEAVY_METAL_ERRATA)), 0, 0, 0, false, false, "Physical"));
        rows.put("light_metal_physical", value(profile(21, 10, 10, 0, Set.of(StatFlag.LIGHT_METAL_ERRATA)), 0, 0, 0, false, false, "Physical"));
        rows.put("heavy_metal_status", value(profile(10, 10, 20, 0, Set.of(StatFlag.HEAVY_METAL_ERRATA)), 0, 0, 0, false, false, "Status"));
        rows.put("light_metal_status", value(profile(10, 10, 19, 0, Set.of(StatFlag.LIGHT_METAL_ERRATA)), 0, 0, 0, false, false, "Status"));
        rows.put("keen_eye_style_ignore", value(profile(17, 10, 10, 0, Set.of()), 5, 0, 0, false, true, "Physical"));
        return rows;
    }

    private static String value(
            CombatantStatProfile stats,
            int physicalBonus,
            int specialBonus,
            int statusBonus,
            boolean suppressPositive,
            boolean ignoreNonStat,
            String category
    ) {
        return Integer.toString(EvasionResolution.resolve(
                new EvasionProfile(stats, physicalBonus, specialBonus, statusBonus, suppressPositive, ignoreNonStat),
                category
        ));
    }

    private static CombatantStatProfile profile(
            int defense,
            int specialDefense,
            int speed,
            int speedStage,
            Set<StatFlag> flags
    ) {
        EnumMap<CombatStat, Integer> bases = new EnumMap<>(CombatStat.class);
        bases.put(CombatStat.DEF, defense);
        bases.put(CombatStat.SPDEF, specialDefense);
        bases.put(CombatStat.SPD, speed);
        EnumMap<CombatStat, Integer> stages = new EnumMap<>(CombatStat.class);
        stages.put(CombatStat.SPD, speedStage);
        return new CombatantStatProfile(bases, stages, Map.of(), flags);
    }
}
