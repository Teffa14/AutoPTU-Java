package io.autoptu.core.oracle;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.Movement;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MovementOracleFixtureTest {
    @Test
    void javaMatchesPinnedPythonMovementOracle() throws IOException {
        String fixturePath = System.getProperty("autoptu.movement.oracle", "").strip();
        Assumptions.assumeFalse(fixturePath.isEmpty(), "Python movement oracle fixture path not configured");

        Map<String, String> expected = readFixtures(Path.of(fixturePath));
        Map<String, String> actual = javaResults();
        assertEquals(expected.keySet(), actual.keySet(), "Python and Java movement scenario sets differ");
        for (String name : expected.keySet()) {
            assertEquals(expected.get(name), actual.get(name), "Movement parity failed for scenario: " + name);
        }
    }

    private static Map<String, String> javaResults() {
        Map<String, String> result = new LinkedHashMap<>();

        result.put("walking_2", coords(Movement.legalShiftTiles(
                new MovementGrid(7, 7, Set.of(), Map.of()),
                MovementProfile.walking(new GridCoord(3, 3), 2)
        )));

        result.put("rough_costs_two", coords(Movement.legalShiftTiles(
                new MovementGrid(4, 1, Set.of(), Map.of(new GridCoord(1, 0), "Rough Grass")),
                MovementProfile.walking(new GridCoord(0, 0), 2)
        )));

        result.put("naturewalk_rough", coords(Movement.legalShiftTiles(
                new MovementGrid(4, 1, Set.of(), Map.of(new GridCoord(1, 0), "Rough Grass")),
                new MovementProfile(
                        new GridCoord(0, 0), 2, 0, 0, 1.0,
                        false, false, false, false, false, true, 0
                )
        )));

        result.put("swim_limit_two", coords(Movement.legalShiftTiles(
                new MovementGrid(
                        4, 1, Set.of(),
                        Map.of(
                                new GridCoord(1, 0), "Water",
                                new GridCoord(2, 0), "Water",
                                new GridCoord(3, 0), "Water"
                        )
                ),
                new MovementProfile(
                        new GridCoord(0, 0), 3, 2, 0, 1.0,
                        false, true, false, false, false, false, 0
                )
        )));

        result.put("mixed_water_then_land", coords(Movement.legalShiftTiles(
                new MovementGrid(
                        4, 1, Set.of(),
                        Map.of(new GridCoord(1, 0), "Water", new GridCoord(2, 0), "Water")
                ),
                new MovementProfile(
                        new GridCoord(0, 0), 3, 2, 0, 1.0,
                        false, true, false, false, false, false, 0
                )
        )));

        result.put("fly_over_blocker_and_rough", coords(Movement.legalShiftTiles(
                new MovementGrid(
                        5, 1,
                        Set.of(new GridCoord(1, 0)),
                        Map.of(new GridCoord(2, 0), "Difficult Rough")
                ),
                new MovementProfile(
                        new GridCoord(0, 0), 0, 0, 3, 1.0,
                        true, false, false, false, false, false, 0
                )
        )));

        result.put("wallrunner_crosses_one", coords(Movement.legalShiftTiles(
                new MovementGrid(5, 1, Set.of(new GridCoord(1, 0)), Map.of()),
                new MovementProfile(
                        new GridCoord(0, 0), 3, 0, 0, 1.0,
                        false, false, false, false, false, false, 1
                )
        )));

        result.put("sprint_after_penalty", coords(Movement.legalShiftTiles(
                new MovementGrid(8, 1, Set.of(), Map.of()),
                new MovementProfile(
                        new GridCoord(0, 0), 5, 0, 0, 1.5,
                        false, false, false, false, false, false, 0
                ),
                2,
                ignored -> true
        )));

        result.put("fit_blocks_expansion", coords(Movement.legalShiftTiles(
                new MovementGrid(5, 1, Set.of(), Map.of()),
                MovementProfile.walking(new GridCoord(0, 0), 4),
                0,
                coord -> !coord.equals(new GridCoord(2, 0))
        )));

        return result;
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

    private static String coords(Set<GridCoord> value) {
        return value.stream()
                .sorted(Comparator.comparingInt(GridCoord::x).thenComparingInt(GridCoord::y))
                .map(coord -> coord.x() + "," + coord.y())
                .collect(Collectors.joining(";"));
    }
}
