package io.autoptu.core.oracle;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.StatFlag;
import io.autoptu.core.model.StatModifier;
import io.autoptu.core.rules.StatResolution;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatResolutionOracleFixtureTest {
    @Test
    void javaMatchesPinnedPythonStatResolutionOracle() throws IOException {
        String fixturePath = System.getProperty("autoptu.stats.oracle", "").strip();
        Assumptions.assumeFalse(fixturePath.isEmpty(), "Python stat-resolution oracle fixture path not configured");
        Map<String, String> expected = readFixtures(Path.of(fixturePath));
        Map<String, String> actual = javaResults();
        assertEquals(expected.keySet(), actual.keySet(), "Python and Java stat scenario sets differ");
        for (String name : expected.keySet()) {
            assertEquals(expected.get(name), actual.get(name), "Stat parity failed for scenario: " + name);
        }
    }

    private static Map<String, String> javaResults() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("offense_physical_stage2", Integer.toString(StatResolution.offensive(profile(Map.of(CombatStat.ATK,12), Map.of(CombatStat.ATK,2), Map.of(), Set.of()), "Physical", false)));
        out.put("offense_modifiers_floor_order", Integer.toString(StatResolution.offensive(profile(Map.of(CombatStat.ATK,10), Map.of(CombatStat.ATK,1), Map.of(CombatStat.ATK,new StatModifier(3,1.5,2)), Set.of()), "Physical", false)));
        out.put("offense_power_shift_physical", Integer.toString(StatResolution.offensive(profile(Map.of(CombatStat.ATK,5,CombatStat.DEF,20), Map.of(CombatStat.DEF,1), Map.of(), Set.of(StatFlag.POWER_SHIFT)), "Physical", false)));
        out.put("offense_power_trick_physical", Integer.toString(StatResolution.offensive(profile(Map.of(CombatStat.ATK,5,CombatStat.DEF,18), Map.of(CombatStat.DEF,-1), Map.of(), Set.of(StatFlag.POWER_TRICK)), "Physical", false)));
        out.put("offense_power_shift_heavy", Integer.toString(StatResolution.offensive(profile(Map.of(CombatStat.DEF,10), Map.of(), Map.of(), Set.of(StatFlag.POWER_SHIFT,StatFlag.HEAVY_METAL_ERRATA)), "Physical", false)));
        out.put("offense_power_shift_light", Integer.toString(StatResolution.offensive(profile(Map.of(CombatStat.DEF,10), Map.of(), Map.of(), Set.of(StatFlag.POWER_SHIFT,StatFlag.LIGHT_METAL_ERRATA)), "Physical", false)));
        out.put("offense_power_shift_special", Integer.toString(StatResolution.offensive(profile(Map.of(CombatStat.SPATK,5,CombatStat.SPDEF,20), Map.of(CombatStat.SPDEF,1), Map.of(), Set.of(StatFlag.POWER_SHIFT)), "Special", false)));
        out.put("offense_flare_boost_burn", Integer.toString(StatResolution.offensive(profile(Map.of(CombatStat.SPATK,12), Map.of(), Map.of(), Set.of(StatFlag.FLARE_BOOST,StatFlag.BURNED)), "Special", false)));
        out.put("offense_ignore_positive", Integer.toString(StatResolution.offensive(profile(Map.of(CombatStat.ATK,12), Map.of(CombatStat.ATK,3), Map.of(), Set.of()), "Physical", true)));
        out.put("defense_burn_physical", Integer.toString(StatResolution.defensive(profile(Map.of(CombatStat.DEF,18), Map.of(), Map.of(), Set.of(StatFlag.BURNED)), "Physical", false)));
        out.put("defense_wonder_room_physical", Integer.toString(StatResolution.defensive(profile(Map.of(CombatStat.DEF,7,CombatStat.SPDEF,22), Map.of(), Map.of(), Set.of(StatFlag.WONDER_ROOM)), "Physical", false)));
        out.put("defense_power_shift_physical", Integer.toString(StatResolution.defensive(profile(Map.of(CombatStat.DEF,5,CombatStat.ATK,15), Map.of(CombatStat.ATK,1), Map.of(), Set.of(StatFlag.POWER_SHIFT)), "Physical", false)));
        out.put("defense_poison_special", Integer.toString(StatResolution.defensive(profile(Map.of(CombatStat.SPDEF,20), Map.of(), Map.of(), Set.of(StatFlag.POISONED)), "Special", false)));
        out.put("defense_potent_override", Integer.toString(StatResolution.defensive(profile(Map.of(CombatStat.SPDEF,20), Map.of(), Map.of(), Set.of(StatFlag.POISONED,StatFlag.POTENT_VENOM_OVERRIDE)), "Special", false)));
        out.put("defense_wonder_special_heavy_light", Integer.toString(StatResolution.defensive(profile(Map.of(CombatStat.DEF,14,CombatStat.SPDEF,5), Map.of(), Map.of(), Set.of(StatFlag.WONDER_ROOM,StatFlag.HEAVY_METAL_ERRATA,StatFlag.LIGHT_METAL_ERRATA)), "Special", false)));
        out.put("speed_stage1", Integer.toString(StatResolution.speed(profile(Map.of(CombatStat.SPD,16), Map.of(CombatStat.SPD,1), Map.of(), Set.of()))));
        out.put("speed_paralyzed", Integer.toString(StatResolution.speed(profile(Map.of(CombatStat.SPD,18), Map.of(), Map.of(), Set.of(StatFlag.PARALYZED,StatFlag.MAJOR_STATUS)))));
        out.put("speed_quick_feet_paralyzed", Integer.toString(StatResolution.speed(profile(Map.of(CombatStat.SPD,16), Map.of(), Map.of(), Set.of(StatFlag.QUICK_FEET,StatFlag.PARALYZED,StatFlag.MAJOR_STATUS)))));
        out.put("speed_heavy", Integer.toString(StatResolution.speed(profile(Map.of(CombatStat.SPD,10), Map.of(), Map.of(), Set.of(StatFlag.HEAVY_METAL_ERRATA)))));
        out.put("speed_light", Integer.toString(StatResolution.speed(profile(Map.of(CombatStat.SPD,10), Map.of(), Map.of(), Set.of(StatFlag.LIGHT_METAL_ERRATA)))));
        return out;
    }

    private static CombatantStatProfile profile(Map<CombatStat,Integer> bases, Map<CombatStat,Integer> stages, Map<CombatStat,StatModifier> mods, Set<StatFlag> flags) {
        EnumMap<CombatStat,Integer> all = new EnumMap<>(CombatStat.class);
        for (CombatStat stat : CombatStat.values()) all.put(stat,10);
        all.putAll(bases);
        return new CombatantStatProfile(all, stages, mods, flags);
    }

    private static Map<String,String> readFixtures(Path path) throws IOException {
        Map<String,String> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t",2);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid stat fixture: " + line);
            out.put(parts[0], parts[1]);
        }
        return out;
    }
}
