package io.autoptu.core.oracle;

import io.autoptu.core.model.AttackModifier;
import io.autoptu.core.rules.Calculations;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculationsOracleFixtureTest {
    @Test
    void javaMatchesPinnedPythonCalculationPrimitives() throws IOException {
        String fixturePath = System.getProperty("autoptu.calculations.oracle", "").strip();
        Assumptions.assumeFalse(fixturePath.isEmpty(), "Python calculation fixture path not configured");

        Map<String, String> expected = readFixtures(Path.of(fixturePath));
        Map<String, String> actual = javaResults();
        assertEquals(expected.keySet(), actual.keySet(), "Python and Java calculation scenario sets differ");
        for (String name : expected.keySet()) {
            assertEquals(expected.get(name), actual.get(name), "Calculation parity failed for scenario: " + name);
        }
    }

    private static Map<String, String> javaResults() {
        Map<String, String> result = new LinkedHashMap<>();

        for (int stage : new int[]{-99, -6, -2, -1, 0, 1, 2, 6, 99}) {
            result.put("clamp_" + stage, Integer.toString(Calculations.clampStage(stage)));
            result.put("stage_mult_" + stage, Double.toString(Calculations.stageMultiplier(stage)));
            result.put("accuracy_stage_" + stage, Integer.toString(Calculations.accuracyStageValue(stage)));
        }

        weather(result, "rain_water", "Rain", "Water");
        weather(result, "storm_electric", " storm ", "ELECTRIC");
        weather(result, "downpour_fire", "Downpour", "Fire");
        weather(result, "sun_fire", "Harsh Sunlight", "Fire");
        weather(result, "sunny_water", "Sunny", "Water");
        weather(result, "hail_ice", "Hail", "Ice");
        weather(result, "sand_rock", "Sandstorm", "Rock");
        weather(result, "rain_grass", "Rain", "Grass");

        result.put("crit20_full", Double.toString(Calculations.critProbability(20, 1.0)));
        result.put("crit18_full", Double.toString(Calculations.critProbability(18, 1.0)));
        result.put("crit18_lowhit", Double.toString(Calculations.critProbability(18, 0.10)));
        result.put("crit_default_zero", Double.toString(Calculations.critProbability(0, 1.0)));

        result.put("burn_physical_101", Integer.toString(Calculations.applyStatusModifiers(101, "Physical", true)));
        result.put("burn_special_101", Integer.toString(Calculations.applyStatusModifiers(101, "Special", true)));
        result.put("not_burned_physical_101", Integer.toString(Calculations.applyStatusModifiers(101, "Physical", false)));

        List<AttackModifier> modifiers = List.of(
                AttackModifier.scalar("half", 0.5),
                AttackModifier.flat("bonus-a", 5),
                AttackModifier.scalar("third", 1.0 / 3.0),
                AttackModifier.flat("bonus-b", 2)
        );
        result.put("flat_then_scalar_floor", Integer.toString(
                Calculations.applyContextDamageModifiers(100, modifiers)
        ));

        result.put("range_melee_first", Calculations.normalizedRangeKind("Melee, 1 Target", "Ranged"));
        result.put("range_cone_first", Calculations.normalizedRangeKind("Cone", "Melee"));
        result.put("range_target_fallback", Calculations.normalizedRangeKind("", "Light Melee"));
        result.put("range_default", Calculations.normalizedRangeKind("", ""));

        return result;
    }

    private static void weather(Map<String, String> result, String name, String weather, String moveType) {
        result.put(name, Integer.toString(Calculations.weatherDbModifier(weather, moveType)));
    }

    private static Map<String, String> readFixtures(Path path) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\t", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid oracle fixture line: " + line);
            }
            result.put(parts[0], parts[1]);
        }
        return result;
    }
}
